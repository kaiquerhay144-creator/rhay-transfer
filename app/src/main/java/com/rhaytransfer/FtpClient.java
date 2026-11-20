package com.rhaytransfer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public final class FtpClient {
    public interface Progress { void onProgress(long sent, long total); }
    private final String host;
    private final int port;
    private Socket control;
    private BufferedReader reader;
    private BufferedWriter writer;

    public FtpClient(String host, int port) { this.host = host; this.port = port; }

    private String response() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("O PS4 encerrou a conexão FTP.");
        StringBuilder result = new StringBuilder(line);
        if (line.length() >= 4 && line.charAt(3) == '-') {
            String end = line.substring(0, 3) + " ";
            while ((line = reader.readLine()) != null) {
                result.append("\n").append(line);
                if (line.startsWith(end)) break;
            }
        }
        return result.toString();
    }

    private String command(String value) throws IOException {
        writer.write(value + "\r\n");
        writer.flush();
        return response();
    }

    private static boolean ok(String r) {
        return r.startsWith("2") || r.startsWith("3");
    }

    public String connectAndLogin() throws IOException {
        control = new Socket();
        control.connect(new InetSocketAddress(host, port), 5000);
        control.setSoTimeout(7000);
        reader = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.US_ASCII));
        writer = new BufferedWriter(new OutputStreamWriter(control.getOutputStream(), StandardCharsets.US_ASCII));
        String greeting = response();
        if (!greeting.startsWith("2")) throw new IOException("Resposta FTP inesperada: " + greeting);
        String user = command("USER anonymous");
        if (user.startsWith("3")) user = command("PASS rhaytransfer@android");
        if (!user.startsWith("2")) throw new IOException("Login FTP recusado: " + user);
        return "FTP conectado e login aceito.";
    }

    public void upload(UriSource source, String remotePath, Progress progress) throws IOException {
        String type = command("TYPE I");
        if (!type.startsWith("2")) throw new IOException("Não foi possível ativar modo binário: " + type);
        int slash = remotePath.lastIndexOf('/');
        if (slash <= 0 || slash == remotePath.length() - 1)
            throw new IOException("Caminho de destino inválido: " + remotePath);
        String directory = remotePath.substring(0, slash);
        String filename = remotePath.substring(slash + 1);
        String cwd = command("CWD " + directory);
        if (!cwd.startsWith("2")) {
            String parent = directory.substring(0, directory.lastIndexOf('/'));
            String folder = directory.substring(directory.lastIndexOf('/') + 1);
            String parentCwd = command("CWD " + parent);
            if (!parentCwd.startsWith("2"))
                throw new IOException("PS4 não permite acessar " + directory + ": " + parentCwd);
            command("MKD " + folder);
            cwd = command("CWD " + directory);
            if (!cwd.startsWith("2"))
                throw new IOException("Não foi possível acessar/criar a pasta " + directory + ": " + cwd);
        }
        String pasv = command("PASV");
        Matcher m = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)").matcher(pasv);
        if (!m.find()) throw new IOException("O PS4 não informou uma conexão passiva: " + pasv);
        String dataHost = m.group(1)+"."+m.group(2)+"."+m.group(3)+"."+m.group(4);
        int dataPort = Integer.parseInt(m.group(5))*256 + Integer.parseInt(m.group(6));
        Socket data = new Socket();
        data.connect(new InetSocketAddress(dataHost, dataPort), 7000);
        String stor = command("STOR " + filename);
        if (!stor.startsWith("1")) { data.close(); throw new IOException("PS4 recusou o envio: " + stor); }
        long total = source.length();
        long sent = 0;
        try (InputStream in = source.open(); OutputStream out = data.getOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                sent += count;
                if (progress != null) progress.onProgress(sent, total);
            }
            out.flush();
        } finally {
            data.close();
        }
        String complete = response();
        if (!complete.startsWith("2")) throw new IOException("Falha ao concluir envio: " + complete);
    }

    public void close() {
        try { if (control != null && control.isConnected()) command("QUIT"); } catch (Exception ignored) {}
        try { if (control != null) control.close(); } catch (Exception ignored) {}
    }

    public interface UriSource {
        InputStream open() throws IOException;
        long length();
    }
}
