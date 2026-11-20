package com.rhaytransfer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class FtpClient {
    public interface Progress { void onProgress(long sent,long total); }
    public interface UriSource { InputStream open() throws IOException; long length(); }
    public static final class RemoteEntry { public final String name; public final boolean directory; RemoteEntry(String n,boolean d){name=n;directory=d;} }
    private final String host; private final int port; private Socket control; private BufferedReader reader; private BufferedWriter writer;
    public FtpClient(String h,int p){host=h;port=p;}
    private String response()throws IOException{String line=reader.readLine();if(line==null)throw new IOException("O PS4 encerrou a conexão FTP.");StringBuilder b=new StringBuilder(line);if(line.length()>=4&&line.charAt(3)=='-'){String end=line.substring(0,3)+" ";while((line=reader.readLine())!=null){b.append("\n").append(line);if(line.startsWith(end))break;}}return b.toString();}
    private String command(String c)throws IOException{writer.write(c+"\r\n");writer.flush();return response();}
    public String connectAndLogin()throws IOException{
        control=new Socket();control.connect(new InetSocketAddress(host,port),5000);control.setSoTimeout(30000);control.setTcpNoDelay(true);
        reader=new BufferedReader(new InputStreamReader(control.getInputStream(),StandardCharsets.US_ASCII));writer=new BufferedWriter(new OutputStreamWriter(control.getOutputStream(),StandardCharsets.US_ASCII));
        String g=response();if(!g.startsWith("2"))throw new IOException("Resposta FTP inesperada: "+g);
        String u=command("USER anonymous");if(u.startsWith("3"))u=command("PASS rhaytransfer@android");if(!u.startsWith("2"))throw new IOException("Login FTP recusado: "+u);return "FTP conectado e login aceito.";
    }
    private Socket dataSocket(String pasv)throws IOException{
        Matcher m=Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)").matcher(pasv);if(!m.find())throw new IOException("Resposta PASV inválida: "+pasv);
        String h=m.group(1)+"."+m.group(2)+"."+m.group(3)+"."+m.group(4);int p=Integer.parseInt(m.group(5))*256+Integer.parseInt(m.group(6));
        Socket s=new Socket();s.setSendBufferSize(1024*1024);s.connect(new InetSocketAddress(h,p),10000);s.setSoTimeout(30000);s.setTcpNoDelay(true);return s;
    }
    public void upload(UriSource src,String remote,Progress progress)throws IOException{
        String type=command("TYPE I");if(!type.startsWith("2"))throw new IOException("Não foi possível ativar modo binário: "+type);
        int slash=remote.lastIndexOf('/');if(slash<=0||slash==remote.length()-1)throw new IOException("Caminho de destino inválido: "+remote);
        String dir=remote.substring(0,slash),name=remote.substring(slash+1);String cwd=command("CWD "+dir);
        if(!cwd.startsWith("2")){String parent=dir.substring(0,dir.lastIndexOf('/'));String folder=dir.substring(dir.lastIndexOf('/')+1);String pc=command("CWD "+parent);if(!pc.startsWith("2"))throw new IOException("Sem acesso a "+dir+": "+pc);command("MKD "+folder);cwd=command("CWD "+dir);if(!cwd.startsWith("2"))throw new IOException("Não foi possível criar/acessar "+dir+": "+cwd);}
        Socket data=dataSocket(command("PASV"));String stor=command("STOR "+name);if(!stor.startsWith("1")){data.close();throw new IOException("PS4 recusou o envio: "+stor);}
        long total=src.length(),sent=0;
        try(InputStream in=src.open();OutputStream out=data.getOutputStream()){byte[] buf=new byte[512*1024];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);sent+=n;if(progress!=null)progress.onProgress(sent,total);}out.flush();}finally{data.close();}
        String done=response();if(!done.startsWith("2"))throw new IOException("Falha ao concluir envio: "+done);
    }
    public List<RemoteEntry> listDirectory(String path)throws IOException{
        String c=command("CWD "+path);if(!c.startsWith("2"))throw new IOException("CWD falhou: "+c);
        Socket data=dataSocket(command("PASV"));String l=command("LIST");if(!l.startsWith("1")){data.close();throw new IOException("LIST recusado: "+l);}
        ArrayList<RemoteEntry> result=new ArrayList<>();try(BufferedReader r=new BufferedReader(new InputStreamReader(data.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){String trimmed=line.trim();if(trimmed.isEmpty())continue;String[] parts=trimmed.split("\\s+",9);if(parts.length<9)continue;String name=parts[8];if(name.equals(".")||name.equals(".."))continue;boolean dir=parts[0].startsWith("d");result.add(new RemoteEntry(name,dir));}}finally{data.close();}
        String done=response();if(!done.startsWith("2"))throw new IOException("LIST não concluiu: "+done);return result;
    }
    public void close(){try{if(control!=null&&control.isConnected())command("QUIT");}catch(Exception ignored){}try{if(control!=null)control.close();}catch(Exception ignored){}}
}
