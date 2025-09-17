# Chat Distribuído em Java

Este projeto é um sistema de chat distribuído, desenvolvido em Java com a biblioteca Swing para a interface gráfica. Ele demonstra a comunicação em tempo real entre múltiplos usuários através de uma arquitetura cliente-servidor, utilizando sockets TCP.

---

## 📚 Finalidade

O objetivo principal deste projeto é servir como uma ferramenta de aprendizado e demonstração de conceitos fundamentais de **sistemas distribuídos** e **programação de redes**. Ele aborda:

-   **Comunicação via Sockets:** Implementação de um protocolo simples sobre TCP/IP para troca de dados.
-   **Concorrência:** Gerenciamento de múltiplos clientes simultaneamente no servidor usando Threads.
-   **Interface Gráfica:** Construção de uma interface de usuário reativa com Java Swing.
-   **Sincronização:** Uso de coleções sincronizadas para garantir a segurança dos dados em um ambiente multithread.

É um projeto ideal para estudantes de ciência da computação, desenvolvedores que desejam praticar programação de redes ou como base para sistemas de comunicação mais complexos.

---

## 🚀 Funcionalidades

-   **Comunicação em Tempo Real:** Troca de mensagens instantâneas.
-   **Múltiplos Modos de Chat:**
    -   **Privado:** Conversas um-a-um.
    -   **Grupo:** Crie e converse em grupos com múltiplos usuários.
    -   **Geral (Broadcast):** Envie mensagens para todos os usuários conectados.
-   **Transferência de Arquivos:** Envie e receba arquivos com uma barra de progresso para acompanhar o upload.
-   **Validação de Usuário:** O sistema impede que dois usuários se conectem com o mesmo nome, solicitando um novo nome sem encerrar a conexão.
-   **Interface Gráfica Intuitiva:**
    -   Janela principal com listas de usuários e grupos online, atualizadas dinamicamente.
    -   Janelas de chat separadas para cada conversa.
    -   Notificações visuais para novas mensagens.
-   **Gerenciamento de Dados em Memória:** Todas as informações (usuários, grupos) são armazenadas em memória no servidor, sendo um sistema sem estado persistente.

---

## 🗂️ Estrutura dos Arquivos

```
Host/
├── Cliente.java   # Lógica do cliente, interface gráfica e comunicação com o servidor.
└── Servidor.java  # Lógica do servidor, gerenciamento de conexões, grupos e roteamento de mensagens.
```

---

## 🖥️ Como Executar

### Pré-requisitos
-   Java Development Kit (JDK) 8 ou superior instalado.

### Passos

1.  **Compile os arquivos Java:**
    Navegue até a pasta do projeto e execute o comando:
    ```sh
    javac Host/Servidor.java Host/Cliente.java
    ```

2.  **Inicie o Servidor:**
    Em um terminal, execute:
    ```sh
    java Host.Servidor
    ```
    O servidor será iniciado e ficará aguardando conexões na porta 8080.

3.  **Inicie um ou mais Clientes:**
    Em um novo terminal para cada cliente, execute:
    ```sh
    java Host.Cliente
    ```
    Uma janela solicitará um nome de usuário para se conectar ao chat.

---

## 💡 Como Funciona

-   **Servidor:** Atua como um hub central. Ele aceita conexões de novos clientes e cria uma `Thread` dedicada para cada um. O servidor é responsável por receber mensagens e arquivos e encaminhá-los para os destinatários corretos, além de manter e distribuir as listas de usuários e grupos ativos.

-   **Cliente:** Conecta-se ao servidor e envia um nome de usuário. Após a validação, a janela principal é exibida. Uma `Thread` em segundo plano fica escutando constantemente por novas mensagens, arquivos e atualizações do servidor, atualizando a interface gráfica conforme necessário.

---

## ⚠️ Observações Importantes

-   **Persistência de Dados:** O sistema **não** utiliza banco de dados. Todas as informações são perdidas quando o servidor é encerrado.
-   **Segurança:** A comunicação não é criptografada. O projeto tem fins didáticos e não deve ser usado em um ambiente de produção sem a implementação de medidas de segurança adequadas.
-   **Limites:** Para evitar sobrecarga, o tamanho dos arquivos é limitado a 10MB e as mensagens a 2048 caracteres.

---

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo `LICENSE` para mais detalhes.
