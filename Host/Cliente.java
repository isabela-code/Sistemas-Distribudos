package Host;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Cliente {

    private static DataOutputStream saida;
    private static DataInputStream entrada;
    private static String nome;

    private static DefaultListModel<String> modeloUsuarios = new DefaultListModel<>();
    private static DefaultListModel<String> modeloGrupos = new DefaultListModel<>();

    private static JFrame framePrincipal;
    private static HashMap<String, JFrame> chatsAbertos = new HashMap<>();

    private static JLabel barraStatus = new JLabel("Conectado");
    private static HashMap<String, java.util.List<String>> historicoMensagens = new HashMap<>();
    private static HashMap<String, Boolean> notificacoesPendentes = new HashMap<>();

    public static void main(String[] args) {
        Socket porta = null;
        try {
            porta = new Socket("26.164.157.141", 8080);
            saida = new DataOutputStream(porta.getOutputStream());
            entrada = new DataInputStream(porta.getInputStream());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Não foi possível conectar ao servidor.");
            System.exit(1);
        }

        nome = null;
        while (true) {
            nome = JOptionPane.showInputDialog("Digite seu nome:");
            if (nome == null || nome.trim().isEmpty()) {
                JOptionPane.showMessageDialog(null, "Nome inválido. Tente novamente.");
                continue;
            }
            try {
                saida.writeUTF(nome);
                saida.flush();
                String resposta = entrada.readUTF();
                if ("OK".equals(resposta)) {
                    break;
                } else if ("ALERTA".equals(resposta)) {
                    String msg = entrada.readUTF();
                    JOptionPane.showMessageDialog(null, msg);
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Erro ao enviar nome ao servidor.");
                System.exit(1);
            }
        }

        criarJanelaPrincipal();

        new Thread(() -> {
            try {
                while (true) {
                    String tipo = entrada.readUTF();
                    switch (tipo) {
                        case "MSG" -> {
                            String remetente = entrada.readUTF();
                            String destino = entrada.readUTF();
                            String msg = entrada.readUTF();

                            if (msg.length() > 2048) msg = msg.substring(0, 2048);

                            String key;
                            boolean grupo = false;
                            if (destino.startsWith("GRUPO:")) {
                                key = destino;
                                grupo = true;
                            } else if (destino.equals("GERAL")) {
                                key = "GERAL";
                            } else {
                                key = remetente;
                            }

                            abrirChat(key, grupo);

                            JFrame chatFrame = chatsAbertos.get(key);
                            JScrollPane scroll = (JScrollPane) chatFrame.getContentPane().getComponent(0);
                            JTextArea area = (JTextArea) scroll.getViewport().getView();

                            if (grupo) area.append(remetente + " (grupo " + destino.substring(6) + "): " + msg + "\n");
                            else if (!destino.equals("GERAL")) area.append(remetente + " : " + msg + "\n");
                            else area.append(remetente + ": " + msg + "\n");

                            if (!chatsAbertos.containsKey(key) || !chatsAbertos.get(key).isVisible()) {
                                notificacoesPendentes.put(key, true);
                                barraStatus.setText("Nova mensagem em " + (grupo ? "grupo " + key.substring(6) : key) + "!");
                                barraStatus.setBackground(new Color(255, 220, 220));
                                JOptionPane.showMessageDialog(framePrincipal, "Nova mensagem de " + remetente + "!", "Notificação", JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                barraStatus.setText("Conectado");
                                barraStatus.setBackground(new Color(230, 240, 255));
                            }
                        }
                        case "FILE" -> {
                            String remetente = entrada.readUTF();
                            String destino = entrada.readUTF();
                            String nomeArquivo = entrada.readUTF();
                            long tamanho = entrada.readLong();

                            if (tamanho > 10 * 1024 * 1024) {
                                JOptionPane.showMessageDialog(null, "Arquivo muito grande recebido: " + nomeArquivo);
                                entrada.skipBytes((int)tamanho);
                                continue;
                            }

                            File arquivoRecebido = new File("recebido_" + nomeArquivo);
                            int tentativas = 0;
                            while (arquivoRecebido.exists() && tentativas < 100) {
                                arquivoRecebido = new File("recebido_" + tentativas + "_" + nomeArquivo);
                                tentativas++;
                            }
                            FileOutputStream fos = new FileOutputStream(arquivoRecebido);
                            byte[] buffer = new byte[4096];
                            long restante = tamanho;
                            while (restante > 0) {
                                int lido = entrada.read(buffer, 0, (int)Math.min(buffer.length, restante));
                                if (lido == -1) throw new IOException("Arquivo incompleto recebido.");
                                fos.write(buffer, 0, lido);
                                restante -= lido;
                            }
                            fos.close();

                            String key;
                            boolean grupo = false;
                            if (destino.startsWith("GRUPO:")) {
                                key = destino;
                                grupo = true;
                            } else if (destino.equals("GERAL")) {
                                key = "GERAL";
                            } else key = remetente;

                            abrirChat(key, grupo);
                            JFrame chatFrame = chatsAbertos.get(key);
                            JScrollPane scroll = (JScrollPane) chatFrame.getContentPane().getComponent(0);
                            JTextArea area = (JTextArea) scroll.getViewport().getView();

                            if (grupo) area.append(remetente + " enviou arquivo no grupo " + destino.substring(6) + ": " + arquivoRecebido.getName() + "\n");
                            else area.append(remetente + " enviou arquivo : " + arquivoRecebido.getName() + "\n");
                        }
                        case "USERS" -> {
                            int qtd = entrada.readInt();
                            if (qtd < 0 || qtd > 1000) break;
                            Set<String> novosUsuarios = new HashSet<>();
                            for (int i = 0; i < qtd; i++) {
                                String u = entrada.readUTF();
                                if (u.length() > 32) u = u.substring(0, 32);
                                novosUsuarios.add(u);
                            }

                            SwingUtilities.invokeLater(() -> {
                                modeloUsuarios.clear();
                                for (String u : novosUsuarios) modeloUsuarios.addElement(u);
                            });
                        }
                        case "GROUPS" -> {
                            int qtd = entrada.readInt();
                            if (qtd < 0 || qtd > 1000) break;
                            Set<String> novosGrupos = new HashSet<>();
                            for (int i = 0; i < qtd; i++) {
                                String g = entrada.readUTF();
                                if (g.length() > 32) g = g.substring(0, 32);
                                novosGrupos.add(g);
                            }

                            SwingUtilities.invokeLater(() -> {
                                modeloGrupos.clear();
                                for (String g : novosGrupos) modeloGrupos.addElement(g);
                            });
                        }
                    }
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Conexão encerrada.");
                System.exit(0);
            }
        }).start();

        javax.swing.Timer timer = new javax.swing.Timer(2000, e -> {
            try {
                saida.writeUTF("PEDIR_ATUALIZACAO");
                saida.flush();
            } catch (IOException ex) { ex.printStackTrace(); }
        });
        timer.start();
    }

    private static void criarJanelaPrincipal() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {}

        framePrincipal = new JFrame("Menu Principal - " + nome);
        framePrincipal.setSize(420, 570);
        framePrincipal.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        framePrincipal.setLayout(new BorderLayout(10, 10));
        framePrincipal.getContentPane().setBackground(new Color(245, 245, 255));

        JPanel listas = new JPanel(new GridLayout(2, 1, 10, 10));
        listas.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        listas.setBackground(new Color(245, 245, 255));

        JList<String> listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setBorder(BorderFactory.createTitledBorder("Usuários"));
        listaUsuarios.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        listaUsuarios.setSelectionBackground(new Color(200, 220, 255));
        listas.add(new JScrollPane(listaUsuarios));

        JList<String> listaGrupos = new JList<>(modeloGrupos);
        listaGrupos.setBorder(BorderFactory.createTitledBorder("Grupos"));
        listaGrupos.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        listaGrupos.setSelectionBackground(new Color(220, 200, 255));
        listas.add(new JScrollPane(listaGrupos));

        framePrincipal.add(listas, BorderLayout.CENTER);

        JButton botaoCriarGrupo = new JButton("Criar Grupo", UIManager.getIcon("FileView.directoryIcon"));
        botaoCriarGrupo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        botaoCriarGrupo.setBackground(new Color(120, 170, 255));
        botaoCriarGrupo.setForeground(Color.WHITE);
        botaoCriarGrupo.setFocusPainted(false);
        botaoCriarGrupo.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        framePrincipal.add(botaoCriarGrupo, BorderLayout.SOUTH);

        barraStatus.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        barraStatus.setBorder(new EmptyBorder(5, 10, 5, 10));
        barraStatus.setOpaque(true);
        barraStatus.setBackground(new Color(230, 240, 255));
        framePrincipal.add(barraStatus, BorderLayout.NORTH);

        listaUsuarios.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && listaUsuarios.getSelectedValue() != null)
                abrirChat(listaUsuarios.getSelectedValue(), false);
        });

        listaGrupos.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && listaGrupos.getSelectedValue() != null)
                abrirChat("GRUPO:" + listaGrupos.getSelectedValue(), true);
        });

        botaoCriarGrupo.addActionListener(e -> {
            String nomeGrupo = JOptionPane.showInputDialog(framePrincipal, "Nome do grupo:");
            if (nomeGrupo == null || nomeGrupo.trim().isEmpty()) return;

            JPanel painel = new JPanel();
            painel.setLayout(new BoxLayout(painel, BoxLayout.Y_AXIS));
            painel.setBackground(new Color(245, 245, 255));
            java.util.List<JCheckBox> checkboxes = new java.util.ArrayList<>();
            for (int i = 0; i < modeloUsuarios.size(); i++) {
                String usuario = modeloUsuarios.getElementAt(i);
                if (!usuario.equals("GERAL") && !usuario.equals(nome)) {
                    JCheckBox cb = new JCheckBox(usuario);
                    cb.setFont(new Font("Segoe UI", Font.PLAIN, 15));
                    cb.setBackground(new Color(245, 245, 255));
                    checkboxes.add(cb);
                    painel.add(cb);
                }
            }

            int opcao = JOptionPane.showConfirmDialog(framePrincipal, new JScrollPane(painel),
                    "Selecione os membros do grupo", JOptionPane.OK_CANCEL_OPTION);
            if (opcao != JOptionPane.OK_OPTION) return;

            java.util.List<String> selecionados = new java.util.ArrayList<>();
            for (JCheckBox cb : checkboxes) if (cb.isSelected()) selecionados.add(cb.getText());
            if (selecionados.isEmpty()) return;

            try {
                saida.writeUTF("CRIAR_GRUPO");
                saida.writeUTF(nomeGrupo);
                saida.writeInt(selecionados.size());
                for (String s : selecionados) saida.writeUTF(s);
                saida.flush();
                JOptionPane.showMessageDialog(framePrincipal, "Grupo criado: " + nomeGrupo);
            } catch (IOException ex) { ex.printStackTrace(); }
        });

        framePrincipal.setLocationRelativeTo(null);
        framePrincipal.setVisible(true);
    }

    private static void abrirChat(String key, boolean grupo) {
        if (key == null || key.isEmpty() || key.length() > 40) return;

        if (chatsAbertos.containsKey(key)) {
            chatsAbertos.get(key).setVisible(true);
            notificacoesPendentes.put(key, false);
            return;
        }

        JFrame chatFrame = new JFrame(grupo ? "Chat Grupo: " + key.substring(6) : "Chat com: " + key);
        chatFrame.setSize(540, 460);
        chatFrame.setLayout(new BorderLayout(10, 10));
        chatFrame.getContentPane().setBackground(new Color(245, 245, 255));

        JTextArea areaMensagens = new JTextArea();
        areaMensagens.setEditable(false);
        areaMensagens.setFont(new Font("Consolas", Font.PLAIN, 15));
        areaMensagens.setBackground(new Color(255, 255, 255));
        JScrollPane scroll = new JScrollPane(areaMensagens);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 220), 2));
        chatFrame.add(scroll, BorderLayout.CENTER);

        if (historicoMensagens.containsKey(key)) {
            for (String msg : historicoMensagens.get(key)) {
                areaMensagens.append(msg + "\n");
            }
        }

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(245, 245, 255));

        // Cria a barra para escrever a mensagem
        JTextField campoMensagem = new JTextField();
        campoMensagem.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        campoMensagem.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 220), 1));
        campoMensagem.setPreferredSize(new Dimension(0, 32));

        // Adiciona a barra de mensagem ao painel, na posição CENTER
        panel.add(campoMensagem, BorderLayout.CENTER);

        JButton botaoEnviar = criarBotao("Enviar", new Color(220, 235, 255));
        JButton botaoArquivo = criarBotao("Enviar arquivo", new Color(235, 220, 255));
        JButton botaoSair = criarBotao("Sair", new Color(255, 220, 220));

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        botoes.setBackground(new Color(245, 245, 255));
        botoes.add(botaoEnviar);
        botoes.add(botaoArquivo);
        botoes.add(botaoSair);

        // Adiciona os botões ao painel, na posição EAST
        panel.add(botoes, BorderLayout.EAST);

        chatFrame.add(panel, BorderLayout.SOUTH);

        JProgressBar barraProgresso = new JProgressBar();
        barraProgresso.setStringPainted(true);
        barraProgresso.setVisible(false);
        chatFrame.add(barraProgresso, BorderLayout.NORTH);

        ActionListener enviar = e -> {
            try {
                String msg = campoMensagem.getText().trim();
                if (msg.isEmpty() || msg.length() > 2048) return;
                saida.writeUTF("MSG");
                saida.writeUTF(key);
                saida.writeUTF(msg);
                saida.flush();
                String texto = "Você: " + msg;
                areaMensagens.append(texto + "\n");
                historicoMensagens.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(texto);
                campoMensagem.setText("");
            } catch (IOException ex) { ex.printStackTrace(); }
        };
        botaoEnviar.addActionListener(enviar);
        campoMensagem.addActionListener(enviar);

        botaoArquivo.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView());
            int retorno = chooser.showOpenDialog(chatFrame);
            if (retorno == JFileChooser.APPROVE_OPTION) {
                File arquivo = chooser.getSelectedFile();
                if (arquivo.length() > 10 * 1024 * 1024) {
                    JOptionPane.showMessageDialog(chatFrame, "Arquivo muito grande. Máximo 10MB.");
                    return;
                }
                barraProgresso.setVisible(true);
                barraProgresso.setValue(0);
                barraProgresso.setMaximum((int) arquivo.length());
                try {
                    saida.writeUTF("FILE");
                    saida.writeUTF(key);
                    saida.writeUTF(arquivo.getName());
                    saida.writeLong(arquivo.length());

                    FileInputStream fis = new FileInputStream(arquivo);
                    byte[] buffer = new byte[4096];
                    int lido, enviado = 0;
                    while ((lido = fis.read(buffer)) > 0) {
                        saida.write(buffer, 0, lido);
                        enviado += lido;
                        barraProgresso.setValue(enviado);
                    }
                    fis.close();
                    saida.flush();
                    barraProgresso.setVisible(false);
                    String texto = "Você enviou arquivo: " + arquivo.getName();
                    areaMensagens.append(texto + "\n");
                    historicoMensagens.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(texto);
                } catch (IOException ex) {
                    barraProgresso.setVisible(false);
                    JOptionPane.showMessageDialog(chatFrame, "Erro ao enviar arquivo.");
                }
            }
        });

        botaoSair.addActionListener(e -> chatFrame.dispose());

        chatFrame.setLocationRelativeTo(null);
        chatFrame.setVisible(true);
        chatsAbertos.put(key, chatFrame);
        notificacoesPendentes.put(key, false);
    }

    private static JButton criarBotao(String texto, Color corFundo) {
        JButton botao = new JButton(texto);
        botao.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        botao.setBackground(corFundo);
        botao.setForeground(Color.DARK_GRAY);
        botao.setFocusPainted(false);
        botao.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 220), 1, true),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return botao;
    }
}