package Host;

import java.io.*;
import java.net.*;
import java.util.*;

public class Servidor {

    private static Map<String, Socket> clientes = Collections.synchronizedMap(new HashMap<>());
    private static Map<String, Set<String>> grupos = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(8080);
            System.out.println("Servidor iniciado na porta 8080");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleCliente(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar ServerSocket: " + e.getMessage());
                }
            }
        }
    }

    private static void handleCliente(Socket socket) {
        String nome = "";
        try {
            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            DataOutputStream saidaCliente = new DataOutputStream(socket.getOutputStream());

            while (true) {
                nome = entrada.readUTF();
                if (nome == null || nome.isEmpty() || nome.length() > 32 || !nome.matches("[\\w\\d_\\-]+")) {
                    saidaCliente.writeUTF("ALERTA");
                    saidaCliente.writeUTF("Nome inválido. Tente novamente.");
                    continue;
                }
                synchronized (clientes) {
                    if (clientes.containsKey(nome)) {
                        saidaCliente.writeUTF("ALERTA");
                        saidaCliente.writeUTF("Nome '" + nome + "' já está em uso. Escolha outro.");
                        continue;
                    }
                    clientes.put(nome, socket);
                    break;
                }
            }

            saidaCliente.writeUTF("OK");
            System.out.println(nome + " entrou no chat.");
            atualizarUsuarios();
            atualizarGruposParaCliente(socket, nome);
            while (true) {
                String tipo;
                try {
                    tipo = entrada.readUTF();
                } catch (IOException e) { break; }
                switch (tipo) {
                    case "MSG" -> {
                        String destino = entrada.readUTF();
                        String mensagem = entrada.readUTF();
                        if (mensagem.length() > 2048) mensagem = mensagem.substring(0, 2048);
                        processarMensagem(nome, destino, mensagem);
                    }
                    case "FILE" -> {
                        String destino = entrada.readUTF();
                        String nomeArquivo = entrada.readUTF();
                        long tamanho = entrada.readLong();
                        if (nomeArquivo.length() > 128) nomeArquivo = nomeArquivo.substring(0, 128);
                        if (tamanho > 10 * 1024 * 1024) {
                            saidaCliente.writeUTF("MSG");
                            saidaCliente.writeUTF("Servidor");
                            saidaCliente.writeUTF(destino);
                            saidaCliente.writeUTF("Arquivo muito grande. Máximo 10MB.");
                            entrada.skipBytes((int)tamanho);
                            continue;
                        }
                        byte[] buffer = new byte[4096];
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        long restante = tamanho;
                        while (restante > 0) {
                            int lido = entrada.read(buffer, 0, (int)Math.min(buffer.length, restante));
                            if (lido == -1) throw new IOException("Arquivo incompleto.");
                            baos.write(buffer, 0, lido);
                            restante -= lido;
                        }
                        processarArquivo(nome, destino, nomeArquivo, baos.toByteArray());
                    }
                    case "CRIAR_GRUPO" -> {
                        String nomeGrupo = entrada.readUTF();
                        if (nomeGrupo.length() > 32) nomeGrupo = nomeGrupo.substring(0, 32);
                        int qtdMembros = entrada.readInt();
                        if (qtdMembros < 1 || qtdMembros > 100) break;
                        Set<String> membros = new HashSet<>();
                        for (int i = 0; i < qtdMembros; i++) {
                            String membro = entrada.readUTF();
                            if (membro.length() > 32) membro = membro.substring(0, 32);
                            membros.add(membro);
                        }
                        membros.add(nome);
                        grupos.put(nomeGrupo, membros);
                        atualizarGrupos();
                        System.out.println("Grupo criado: " + nomeGrupo + " -> " + membros);
                    }
                    case "PEDIR_ATUALIZACAO" -> {
                        atualizarUsuariosParaCliente(socket);
                        atualizarGruposParaCliente(socket, nome);
                    }
                    case "DESCONECTAR" -> { return; }
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + nome);
        } finally {
            if (!nome.isEmpty()) {
                clientes.remove(nome);
                atualizarUsuarios();
                atualizarGrupos();
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void processarMensagem(String remetente, String destino, String mensagem) {
        try {
            if (destino.equals("GERAL")) broadcast(remetente, mensagem);
            else if (destino.startsWith("GRUPO:")) sendGroup(remetente, destino.substring(6), mensagem);
            else sendPrivate(remetente, destino, mensagem);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void processarArquivo(String remetente, String destino, String nomeArquivo, byte[] arquivoBytes) {
        try {
            if (destino.equals("GERAL")) sendFileBroadcast(remetente, nomeArquivo, arquivoBytes);
            else if (destino.startsWith("GRUPO:")) sendFileGroup(remetente, destino.substring(6), nomeArquivo, arquivoBytes);
            else sendFile(remetente, destino, nomeArquivo, arquivoBytes);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void broadcast(String remetente, String mensagem) throws IOException {
        synchronized (clientes) {
            for (Map.Entry<String, Socket> entry : clientes.entrySet()) {
                DataOutputStream saida = new DataOutputStream(entry.getValue().getOutputStream());
                saida.writeUTF("MSG");
                saida.writeUTF(remetente);
                saida.writeUTF("GERAL");
                saida.writeUTF(mensagem);
            }
        }
    }

    private static void sendPrivate(String remetente, String destinatario, String mensagem) throws IOException {
        Socket socketDest = clientes.get(destinatario);
        if (socketDest != null) {
            DataOutputStream saidaDest = new DataOutputStream(socketDest.getOutputStream());
            saidaDest.writeUTF("MSG");
            saidaDest.writeUTF(remetente);
            saidaDest.writeUTF(destinatario);
            saidaDest.writeUTF(mensagem);
        }
    }

    private static void sendGroup(String remetente, String grupo, String mensagem) throws IOException {
        Set<String> membros = grupos.get(grupo);
        if (membros != null) {
            for (String m : membros) {
                if (!m.equals(remetente)) {
                    Socket socketDest = clientes.get(m);
                    if (socketDest != null) {
                        DataOutputStream saidaDest = new DataOutputStream(socketDest.getOutputStream());
                        saidaDest.writeUTF("MSG");
                        saidaDest.writeUTF(remetente);
                        saidaDest.writeUTF("GRUPO:" + grupo);
                        saidaDest.writeUTF(mensagem);
                    }
                }
            }
        }
    }

    private static void sendFile(String remetente, String destinatario, String nomeArquivo, byte[] arquivoBytes) throws IOException {
        Socket socketDest = clientes.get(destinatario);
        if (socketDest != null) {
            DataOutputStream saidaDest = new DataOutputStream(socketDest.getOutputStream());
            saidaDest.writeUTF("FILE");
            saidaDest.writeUTF(remetente);
            saidaDest.writeUTF(destinatario);
            saidaDest.writeUTF(nomeArquivo);
            saidaDest.writeLong(arquivoBytes.length);
            saidaDest.write(arquivoBytes);
        }
    }

    private static void sendFileGroup(String remetente, String grupo, String nomeArquivo, byte[] arquivoBytes) throws IOException {
        Set<String> membros = grupos.get(grupo);
        if (membros != null) {
            for (String m : membros) {
                if (!m.equals(remetente)) {
                    Socket socketDest = clientes.get(m);
                    if (socketDest != null) {
                        DataOutputStream saidaDest = new DataOutputStream(socketDest.getOutputStream());
                        saidaDest.writeUTF("FILE");
                        saidaDest.writeUTF(remetente);
                        saidaDest.writeUTF("GRUPO:" + grupo);
                        saidaDest.writeUTF(nomeArquivo);
                        saidaDest.writeLong(arquivoBytes.length);
                        saidaDest.write(arquivoBytes);
                    }
                }
            }
        }
    }

    private static void sendFileBroadcast(String remetente, String nomeArquivo, byte[] arquivoBytes) throws IOException {
        synchronized (clientes) {
            for (Map.Entry<String, Socket> entry : clientes.entrySet()) {
                if (entry.getKey().equals(remetente)) continue;
                DataOutputStream saida = new DataOutputStream(entry.getValue().getOutputStream());
                saida.writeUTF("FILE");
                saida.writeUTF(remetente);
                saida.writeUTF("GERAL");
                saida.writeUTF(nomeArquivo);
                saida.writeLong(arquivoBytes.length);
                saida.write(arquivoBytes);
            }
        }
    }

    private static void atualizarUsuarios() {
        synchronized (clientes) {
            for (Map.Entry<String, Socket> entry : clientes.entrySet()) {
                try { atualizarUsuariosParaCliente(entry.getValue()); } 
                catch (IOException e) { clientes.remove(entry.getKey()); }
            }
        }
    }

    private static void atualizarUsuariosParaCliente(Socket socket) throws IOException {
        DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
        saida.writeUTF("USERS");
        int total = clientes.size();
        if (total > 1000) total = 1000;
        saida.writeInt(total);
        int count = 0;
        for (String u : clientes.keySet()) {
            if (count++ >= total) break;
            if (u.length() > 32) u = u.substring(0, 32);
            saida.writeUTF(u);
        }
    }

    private static void atualizarGrupos() {
        synchronized (clientes) {
            for (Map.Entry<String, Socket> entry : clientes.entrySet()) {
                try { atualizarGruposParaCliente(entry.getValue(), entry.getKey()); }
                catch (IOException e) { clientes.remove(entry.getKey()); }
            }
        }
    }

    private static void atualizarGruposParaCliente(Socket socket, String cliente) throws IOException {
        DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
        saida.writeUTF("GROUPS");
        List<String> visiveis = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : grupos.entrySet()) {
            if (entry.getValue().contains(cliente)) visiveis.add(entry.getKey());
        }
        int total = visiveis.size();
        if (total > 1000) total = 1000;
        saida.writeInt(total);
        int count = 0;
        for (String g : visiveis) {
            if (count++ >= total) break;
            if (g.length() > 32) g = g.substring(0, 32);
            saida.writeUTF(g);
        }
    }
}
