import java.io.*;
import java.net.*;
import java.util.ArrayList;


public class NetworkNode {

    protected static ServerSocket serverSocket;
    protected static DatagramSocket datagramSocket;
    protected static String identifier;
    protected static String gateway;
    protected static int port;
    protected static int tcpport;
    protected static ServerNode selectedNode = null;
    static ArrayList<ServerNode> serverNodeList = new ArrayList<>();
    static ArrayList<Item> Items = new ArrayList<>();

    static void log(String msg) {
        System.out.println("[S]: " + msg);
    }

    public static void main(String[] args) throws IOException {
        // Parameter scan loop
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-ident" -> identifier = args[++i];
                case "-tcpport" -> {
                    String tcpPortArr = args[++i];
                    tcpport = Integer.parseInt(tcpPortArr);
                }
                case "-gateway" -> {
                    String[] gatewayArray = args[++i].split(":");
                    gateway = gatewayArray[0];
                    port = Integer.parseInt(gatewayArray[1]);
                }
            }
            if (args[i].length() > 2 && (args[i].charAt(0) >= 65 && args[i].charAt(0) <= 90)) {
                Items.add(new Item(args[i].split(":")[0], Integer.parseInt(args[i].split(":")[1])));
            }
        }

        //uruchamiane serwera i praca z klientami
        serverSocket = new ServerSocket(tcpport);
        datagramSocket = new DatagramSocket(tcpport);
        log(identifier + " " + gateway + ":" + port + " tcp:" + tcpport + Items.toString());
        log("Serwer działa, oczekiwanie na klienta.");
        TCPServerThread TCPThread = new TCPServerThread();
        TCPThread.start();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        if (bufferedReader.readLine().equals("close")) {
            serverSocket.close();
            log("Wyłączono wątek serwera");
        }

    }

    private record ServerNode(String identifier, String gateway, int port) {
        @Override
        public String toString() {
            return "ServerNode{" +
                    "identifier='" + identifier + '\'' +
                    ", gateway='" + gateway + '\'' +
                    ", port=" + port +
                    '}';
        }
    }

    private static class Item {
        String name;
        int quantity;

        public Item(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return name + ":" + quantity;

        }
    }

    private static class TCPServerThread extends Thread {

        public TCPServerThread() {
        }

        static void log(String msg) {
            System.out.println("[S]: " + msg);
        }

        static void sendMSG(String msg, BufferedWriter bW) throws IOException {
            bW.write(msg);
            bW.newLine();
            bW.flush();
        }

        @Override
        public void run() {
            try {
                UDPServerThread udpServerThread = new UDPServerThread();
                udpServerThread.start();
                while (true) {
                    Socket socket = serverSocket.accept(); // TCP

                    OutputStream outputStream = socket.getOutputStream();
                    InputStream inputStream = socket.getInputStream();
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedWriter bW = new BufferedWriter(outputStreamWriter);
                    BufferedReader bR = new BufferedReader(inputStreamReader);
                    String clientIP = socket.getInetAddress().toString();
                    int clientPORT = socket.getPort();
                    String msg;
                    log("Połączono z: " + clientIP + " " + clientPORT);
                    msg = bR.readLine();
                   // log(msg);

                    if (msg.equals("NEW_NODE")) {
                        String nodeID = bR.readLine();
                        String nodeGateway = bR.readLine();
                        int nodeTCPPort = Integer.parseInt(bR.readLine());
                        boolean isOnList = false;
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.identifier.equals(nodeID) || identifier.equals(nodeID)) {
                                isOnList = true;
                                break;
                            }
                        }
                        if (!isOnList) {
                            serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort));
                        }
                        if (gateway != null) {
                            udpServerThread.sendListToParent();
                        }
                        continue;
                    }

                    if (msg.equals("NEW_LIST")) {
                        int iterate = Integer.parseInt(bR.readLine());
                        for (int i = 0; i < iterate; i++) {
                            String nodeID = bR.readLine();
                            String nodeGateway = bR.readLine();
                            int nodeTCPPort = Integer.parseInt(bR.readLine());
                            boolean isOnList = false;
                            for (ServerNode serverNode : serverNodeList) {
                                if (serverNode.identifier.equals(nodeID) || identifier.equals(nodeID)) {
                                    isOnList = true;
                                    break;
                                }
                            }
                            if (!isOnList) {
                                serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort));
                            }
                        }

                        if (gateway == null) {
                            udpServerThread.sendUpdatedList();
                        }
                        continue;
                    }

                    if (msg.equals("UPDATED_LIST")) {
                        int iterate = Integer.parseInt(bR.readLine());
                        for (int i = 0; i < iterate; i++) {
                            String nodeID = bR.readLine();
                            String nodeGateway = bR.readLine();
                            int nodeTCPPort = Integer.parseInt(bR.readLine());
                            boolean isOnList = false;
                            for (ServerNode serverNode : serverNodeList) {
                                if (serverNode.identifier.equals(nodeID) || identifier.equals(nodeID)) {
                                    isOnList = true;
                                    break;
                                }
                            }
                            if (!isOnList) {
                                serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort));
                            }
                        }
                        continue;
                    }

                    if (msg.equals("REQUEST")) {
                        String item = bR.readLine();
                        int quantity = Integer.parseInt(bR.readLine());
                        DatagramPacket datagramPacket;
                        for (Item itemToCheck : Items) {
                            if (itemToCheck.quantity >= quantity && itemToCheck.name.equals(item)) {
                                datagramPacket = new DatagramPacket("TRUE".getBytes(), "TRUE".getBytes().length, datagramSocket.getInetAddress(), datagramSocket.getPort());
                            } else {
                                datagramPacket = new DatagramPacket("FALSE".getBytes(), "FALSE".getBytes().length, datagramSocket.getInetAddress(), datagramSocket.getPort());
                            }
                            datagramSocket.send(datagramPacket);
                        }
                        continue;
                    }

                    if (msg.equals("GET_ITEM")) {
                        String item = bR.readLine();
                        int quantity = Integer.parseInt(bR.readLine());
                        for (Item itemToCheck : Items) {
                            if (itemToCheck.quantity >= quantity && itemToCheck.name.equals(item)) {
                                itemToCheck.quantity -= quantity;
                            }
                        }
                        continue;
                    }

                    if (msg.equals("TERMINATE")) {
                        log("Klient zakończył połączenie");
                        continue;
                    }


                    log(msg);
                    ArrayList<Item> clientItems = new ArrayList<>();
                    for (int i = 1; i < msg.split(" ").length; i++) {
                        clientItems.add(new Item(msg.split(" ")[i].split(":")[0], Integer.parseInt(msg.split(" ")[i].split(":")[1])));
                    }
                    ArrayList<ServerNode> clientNodes = new ArrayList<>();
                    ArrayList<Boolean> isItemOK = new ArrayList<>();
                    boolean isInMyItems = false;
                    for (Item clientItem : clientItems) {
                        for (Item item : Items) {
                            if (clientItem.name.equals(item.name) && item.quantity >= clientItem.quantity) {
                                isItemOK.add(true);
                                clientNodes.add(null);
                                isInMyItems = true;
                            }
                        }
                        if (!isInMyItems) {
                            clientNodes.add(udpServerThread.sendRequest(clientItem.name, clientItem.quantity));
                            isItemOK.add(false);
                        }
                        isInMyItems = false;
                    }
                    boolean[] tableOfYes = new boolean[isItemOK.size()];
                    for (int i = 0; i < isItemOK.size(); i++) {
                        tableOfYes[i] = (isItemOK.get(i) || clientNodes.get(i) != null);
                    }
                    boolean AreAllYes = true;
                    for (boolean tableOfYe : tableOfYes) {
                        if (!tableOfYe) {
                            AreAllYes = false;
                            break;
                        }

                    }

                    if (AreAllYes) {
                        for (int i = 0; i < clientItems.size(); i++) {
                            if (isItemOK.get(i)) {
                                for (Item item : Items) {
                                    if (item.name.equals(clientItems.get(i).name)) {
                                        item.quantity -= clientItems.get(i).quantity;
                                        log("Pozostało " + item.quantity + " zasobu " + item.name);
                                        sendMSG(clientItems.get(i).name + ":" + clientItems.get(i).quantity + ":" + (socket.getRemoteSocketAddress().toString().split("/")[1]).split(":")[0] + ":" + socket.getLocalPort(), bW);
                                    }
                                }
                            } else {
                                assert clientNodes.get(i) != null;
                                udpServerThread.getItem(clientNodes.get(i), clientItems.get(i).name, clientItems.get(i).quantity);
                                sendMSG(clientItems.get(i).name + ":" + clientItems.get(i).quantity + ":" + clientNodes.get(i).gateway + ":" + clientNodes.get(i).port, bW);
                            }
                        }

                        log("Wysłano zasoby");
                    } else {
                        sendMSG("FAILED", bW);
                        log("Niewystarczająca ilośc zasobów do wysłania");
                    }

                    socket.close();
                    log("Closed");
                }
            } catch (IOException e) {
                System.out.println(this.getName() + " został wyłączony.");
            }
        }

    }

    private static class UDPServerThread extends TCPServerThread {

        byte[] buff = new byte[1460];
        DatagramPacket datagram = new DatagramPacket(buff, buff.length);

        public UDPServerThread() {
            super();
        }


        ServerNode sendRequest(String item, int quantity) {
            try {
                for (ServerNode serverNode : serverNodeList) {
                    DatagramPacket datagramPacket = new DatagramPacket("REQUEST".getBytes(), "REQUEST".getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(item.getBytes(), item.getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(quantity).getBytes(), String.valueOf(quantity).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println(selectedNode);
                    return selectedNode;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        void getItem(ServerNode node, String item, int quantity) {
            try {
                DatagramPacket datagramPacket = new DatagramPacket("GET_ITEM".getBytes(), "GET_ITEM".getBytes().length, InetAddress.getByName(node.gateway), node.port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(item.getBytes(), item.getBytes().length, InetAddress.getByName(node.gateway), node.port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(quantity).getBytes(), String.valueOf(quantity).getBytes().length, InetAddress.getByName(node.gateway), node.port);
                datagramSocket.send(datagramPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void sendListToParent() {
            try {
                DatagramPacket datagramPacket = new DatagramPacket("NEW_LIST".getBytes(), "NEW_LIST".getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(serverNodeList.toArray().length + 1).getBytes(), String.valueOf(serverNodeList.toArray().length + 1).getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(identifier).getBytes(), String.valueOf(identifier).getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(datagramSocket.getLocalAddress().getHostAddress().getBytes(), datagramSocket.getLocalAddress().getHostAddress().getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(tcpport).getBytes(), String.valueOf(tcpport).getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                for (ServerNode serverNode : serverNodeList) {
                    datagramPacket = new DatagramPacket(serverNode.identifier.getBytes(), serverNode.identifier.getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(serverNode.gateway.getBytes(), serverNode.gateway.getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(serverNode.port).getBytes(), String.valueOf(serverNode.port).getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void sendUpdatedList() {
            try {
                for (ServerNode serverNode : serverNodeList) {
                    DatagramPacket datagramPacket = new DatagramPacket("UPDATED_LIST".getBytes(), "UPDATED_LIST".getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(serverNodeList.toArray().length + 1).getBytes(), String.valueOf(serverNodeList.toArray().length + 1).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(identifier.getBytes(), identifier.getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(InetAddress.getLocalHost().getHostAddress().getBytes(), InetAddress.getLocalHost().getHostAddress().getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(tcpport).getBytes(), String.valueOf(tcpport).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    for (ServerNode serverNode2 : serverNodeList) {
                        datagramPacket = new DatagramPacket(serverNode2.identifier.getBytes(), serverNode2.identifier.getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(serverNode2.gateway.getBytes(), serverNode2.gateway.getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(String.valueOf(serverNode2.port).getBytes(), String.valueOf(serverNode2.port).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public synchronized void start() {
            if (gateway != null) {
                try {
                    Socket netSocket = new Socket(gateway, port);
                    PrintWriter out = new PrintWriter(netSocket.getOutputStream(), true);
                    out.println("NEW_NODE");
                    out.println(identifier);
                    out.println(netSocket.getLocalAddress().getHostAddress());
                    out.println(tcpport);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            super.start();
        }


        @Override
        public void run() {
            while (true) {
                try {
                    datagramSocket.receive(datagram); //UDP
                    //log(new String(datagram.getData(), 0, datagram.getLength()));

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("TRUE")) {
                        datagramSocket.receive(datagram);
                        String nodeID = new String(datagram.getData(), 0, datagram.getLength());
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.identifier.equals(nodeID)) {
                                selectedNode = serverNode;
                            }
                        }
                    }

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("NEW_NODE")) {
                        datagramSocket.receive(datagram);
                        String nodeID = new String(datagram.getData(), 0, datagram.getLength());
                        datagramSocket.receive(datagram);
                        String nodeGateway = new String(datagram.getData(), 0, datagram.getLength());
                        datagramSocket.receive(datagram);
                        int nodeTCPPort = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        boolean isOnList = false;
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.identifier.equals(nodeID) || identifier.equals(nodeID)) {
                                isOnList = true;
                                break;
                            }
                        }
                        if (!isOnList) {
                            serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort));
                        }
                        if (gateway != null) {
                            sendListToParent();
                        }
                        continue;
                    }

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("NEW_LIST")) {
                        datagramSocket.receive(datagram);
                        int iterate = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        for (int i = 0; i < iterate; i++) {
                            datagramSocket.receive(datagram);
                            String nodeID = new String(datagram.getData(), 0, datagram.getLength());
                            datagramSocket.receive(datagram);
                            String nodeGateway = new String(datagram.getData(), 0, datagram.getLength());
                            datagramSocket.receive(datagram);
                            int nodeTCPPort = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                            boolean isOnList = false;
                            for (ServerNode serverNode : serverNodeList) {
                                if (serverNode.identifier.equals(nodeID) || identifier.equals(nodeID)) {
                                    isOnList = true;
                                    break;
                                }
                            }
                            if (!isOnList) {
                                serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort));
                            }
                        }

                        if (gateway == null) {
                            sendUpdatedList();
                        }
                        continue;
                    }

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("UPDATED_LIST")) {
                        datagramSocket.receive(datagram);
                        int iterate = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        for (int i = 0; i < iterate; i++) {
                            datagramSocket.receive(datagram);
                            String nodeID = new String(datagram.getData(), 0, datagram.getLength());
                            datagramSocket.receive(datagram);
                            String nodeGateway = new String(datagram.getData(), 0, datagram.getLength());
                            datagramSocket.receive(datagram);
                            int nodeTCPPort = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                            boolean isOnList = false;
                            for (ServerNode serverNode : serverNodeList) {
                                if (serverNode.identifier.equals(nodeID) || identifier.equals(nodeID)) {
                                    isOnList = true;
                                    break;
                                }
                            }
                            if (!isOnList) {
                                serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort));
                            }
                        }
                        continue;
                    }

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("REQUEST")) {
                        datagramSocket.receive(datagram);
                        String item = new String(datagram.getData(), 0, datagram.getLength());
                        datagramSocket.receive(datagram);
                        int quantity = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        DatagramPacket datagramPacket;
                        for (Item itemToCheck : Items) {
                            if (itemToCheck.quantity >= quantity && itemToCheck.name.equals(item)) {
                                datagramPacket = new DatagramPacket("TRUE".getBytes(), "TRUE".getBytes().length, datagram.getAddress(), datagram.getPort());
                                datagramSocket.send(datagramPacket);
                                datagramPacket = new DatagramPacket(identifier.getBytes(), identifier.getBytes().length, datagram.getAddress(), datagram.getPort());
                            } else {
                                datagramPacket = new DatagramPacket("FALSE".getBytes(), "FALSE".getBytes().length, datagram.getAddress(), datagram.getPort());
                            }
                            datagramSocket.send(datagramPacket);

                        }
                        continue;
                    }

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("GET_ITEM")) {
                        datagramSocket.receive(datagram);
                        String item = new String(datagram.getData(), 0, datagram.getLength());
                        datagramSocket.receive(datagram);
                        int quantity = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        for (Item itemToCheck : Items) {
                            if (itemToCheck.quantity >= quantity && itemToCheck.name.equals(item)) {
                                itemToCheck.quantity -= quantity;
                                log("Pozostało " + itemToCheck.quantity + " zasobu " + itemToCheck.name);
                            }
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }
}


