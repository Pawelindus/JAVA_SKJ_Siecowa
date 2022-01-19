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
    static ArrayList<ServerNode> serverNodeList = new ArrayList<>();
    static ArrayList<Item> Items = new ArrayList<>();
    static TCPServerThread TCPThread = new TCPServerThread();
    static UDPServerThread UDPThread = new UDPServerThread();
    private static boolean isTCP;

    static void log(String msg) {
        System.out.println("[S]: " + msg);
    }

    public static void main(String[] args) throws IOException {
        //Pętla skanująca parametry (args)
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-ident": {
                    identifier = args[++i];
                    break;
                }
                case "-udpport": {
                    String tcpPortArr = args[++i];
                    tcpport = Integer.parseInt(tcpPortArr);
                    isTCP = false;
                    break;
                }
                case "-tcpport": {
                    String tcpPortArr = args[++i];
                    tcpport = Integer.parseInt(tcpPortArr);
                    isTCP = true;
                    break;
                }
                case "-gateway": {
                    String[] gatewayArray = args[++i].split(":");
                    gateway = gatewayArray[0];
                    port = Integer.parseInt(gatewayArray[1]);
                    break;
                }
            }
            if (args[i].length() > 2 && (args[i].charAt(0) >= 65 && args[i].charAt(0) <= 90)) {
                Items.add(new Item(args[i].split(":")[0], Integer.parseInt(args[i].split(":")[1])));
            }
        }

        //uruchamiane serwera i praca z klientami, startuje wątek UDP(komunikacja wewnętrzna) oraz
        //TCP(komunikacja zewnętrzna)
        serverSocket = new ServerSocket(tcpport);
        datagramSocket = new DatagramSocket(tcpport);
        log(identifier + " " + gateway + ":" + port + " tcp:" + tcpport + Items.toString());
        log("Serwer działa, oczekiwanie na klienta.");
        TCPThread.start();
        UDPThread.start();


        //pozwala używać komend do debugowania
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String command = bufferedReader.readLine();
            if (command.equals("close")) {
                serverSocket.close();
                log("Wyłączono wątek serwera");
            }
            if (command.equals("showNodes")) {
                for (ServerNode serverNode : serverNodeList) {
                    log(serverNode.toString());
                    for (Item item : serverNode.getItems()) {
                        log(item.toString());
                    }
                }
            }
            if (command.equals("isTCP")) {
                System.out.println("isTCP: " + isTCP);
            }
        }
    }

    //Klasa pozwalająca przechować informacje o innych Node w sieci (id, ip, port oraz jakie ma zasoby)
    private static class ServerNode {

        protected String identifier;
        protected String gateway;
        protected int port;
        protected ArrayList<Item> itemArrayList;
        protected Item oneItem;

        public ServerNode(String identifier, String gateway, int port, ArrayList<Item> itemArrayList) {
            this.identifier = identifier;
            this.gateway = gateway;
            this.port = port;
            this.itemArrayList = itemArrayList;
        }

        public ServerNode(String identifier, String gateway, int port, Item oneItem) {
            this.identifier = identifier;
            this.gateway = gateway;
            this.port = port;
            this.oneItem = oneItem;
        }

        public ArrayList<Item> getItems() {
            return itemArrayList;
        }

        @Override
        public String toString() {
            return "ServerNode{" +
                    "identifier='" + identifier + '\'' +
                    ", gateway='" + gateway + '\'' +
                    ", port=" + port +
                    '}';
        }
    }

    //Klasa przedstawiająca zasób (nazwa, ilość)
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

    //Klasa wątku TCP, pozwala na obsługę klienta, sprawdzenie wymaganych zasobów oraz odpowiadanie klientowi
    private static class TCPServerThread extends Thread {

        public TCPServerThread() {
        }

        //Pozwala wypisywać logi w konsoli
        static void log(String msg) {
            System.out.println("[S]: " + msg);
        }

        //Uproszczona funkcja wysyłania w celu ułatwienia pracy
        static void sendMSG(String msg, BufferedWriter bW) throws IOException {
            bW.write(msg);
            bW.newLine();
            bW.flush();
        }

        //Funkcja wysyłająca prośbę o aktualizację informacji o zasobach
        synchronized void sendRequest(String item, int quantity, String ident) {
            try {
                for (ServerNode serverNode : serverNodeList) {
                    Socket netSocket = new Socket(serverNode.gateway, serverNode.port);
                    PrintWriter out = new PrintWriter(netSocket.getOutputStream(), true);
                    String message = "REQUEST" + " " + quantity + " " + item + " " + ident;
                    out.println(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Odsyła "Dziecku" informacje o sobie, informuje je o dodaniu do sieci
        synchronized void sendAccepted() {
            try {
                Socket netSocket = new Socket(serverNodeList.get(serverNodeList.size() - 1).gateway, serverNodeList.get(serverNodeList.size() - 1).port);
                PrintWriter out = new PrintWriter(netSocket.getOutputStream(), true);
                out.println("ACCEPTED");
                out.println(identifier);
                out.println("127.0.0.1");
                out.println(tcpport);
                out.println(Items.size());
                for (Item item : Items) {
                    out.println(item.name);
                    out.println(item.quantity);
                }
            } catch (Exception ignored) {
            }
        }

        //Funkcja usuwająca ilość posiadanych zasobów danego typu
        synchronized void getItem(ServerNode node, String item, int quantity) {
            try {
                Socket netSocket = new Socket(node.gateway, node.port);
                PrintWriter out = new PrintWriter(netSocket.getOutputStream(), true);
                out.println("GET_ITEM");
                out.println(item);
                out.println(quantity);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Funkcja wysyłająca informację do "Rodzica" o pojawieniu się nowego Node niżej
        synchronized void sendListToParent() {
            try {
                Socket netSocket = new Socket(gateway, port);
                PrintWriter out = new PrintWriter(netSocket.getOutputStream(), true);
                out.println("NEW_LIST");
                out.println(serverNodeList.toArray().length + 1);
                out.println(identifier);
                out.println("127.0.0.1");
                out.println(tcpport);
                out.println(Items.size());
                for (Item item : Items) {
                    out.println(item.name);
                    out.println(item.quantity);
                }
                for (ServerNode serverNode : serverNodeList) {
                    out.println(serverNode.identifier);
                    out.println(serverNode.gateway);
                    out.println(serverNode.port);
                    out.println(serverNode.itemArrayList.size());
                    for (Item item : serverNode.itemArrayList) {
                        out.println(item.name);
                        out.println(item.quantity);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Funkcja wysyłająca do wszystkich aktualnie istniejących Node aktualnej bazy informacji o innych Node'ach
        synchronized void sendUpdatedList() {
            try {
                for (ServerNode serverNode : serverNodeList) {
                    Socket netSocket = new Socket(serverNode.gateway, serverNode.port);
                    PrintWriter out = new PrintWriter(netSocket.getOutputStream(), true);
                    out.println("UPDATED_LIST");
                    out.println(serverNodeList.toArray().length + 1);
                    out.println(identifier);
                    out.println("127.0.0.1");
                    out.println(tcpport);
                    out.println(Items.size());
                    for (Item item : Items) {
                        out.println(item.name);
                        out.println(item.quantity);
                    }
                    for (ServerNode serverNode2 : serverNodeList) {
                        out.println(serverNode2.identifier);
                        out.println(serverNode2.gateway);
                        out.println(serverNode2.port);
                        out.println(serverNode2.itemArrayList.size());
                        for (Item item : serverNode2.itemArrayList) {
                            out.println(item.name);
                            out.println(item.quantity);
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
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

                    if (msg.equals("NEW_NODE")) {
                        String nodeID = bR.readLine();
                        String nodeGateway = bR.readLine();
                        int nodeTCPPort = Integer.parseInt(bR.readLine());
                        int numberOfItems = Integer.parseInt(bR.readLine());
                        ArrayList<Item> arrayList = new ArrayList<>();
                        for (int i = 0; i < numberOfItems; i++) {
                            String name = bR.readLine();
                            int quantity = Integer.parseInt(bR.readLine());
                            arrayList.add(new Item(name, quantity));
                        }
                        boolean isOnList = false;
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.port == nodeTCPPort || tcpport == nodeTCPPort) {
                                isOnList = true;
                                break;
                            }
                        }
                        if (!isOnList) {
                            serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort, arrayList));
                        }
                        sendAccepted();
                        if (gateway != null) {
                            sendListToParent();
                        }
                        continue;
                    }

                    if (msg.equals("ACCEPTED")) {
                        String nodeID = bR.readLine();
                        String nodeGateway = bR.readLine();
                        int nodeTCPPort = Integer.parseInt(bR.readLine());
                        int numberOfItems = Integer.parseInt(bR.readLine());
                        ArrayList<Item> arrayList = new ArrayList<>();
                        for (int i = 0; i < numberOfItems; i++) {
                            String name = bR.readLine();
                            int quantity = Integer.parseInt(bR.readLine());
                            arrayList.add(new Item(name, quantity));
                        }
                        boolean isOnList = false;
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.port == nodeTCPPort || tcpport == nodeTCPPort) {
                                isOnList = true;
                                break;
                            }
                        }
                        if (!isOnList) {
                            serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort, arrayList));
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
                            int numberOfItems = Integer.parseInt(bR.readLine());
                            ArrayList<Item> arrayList = new ArrayList<>();
                            for (int j = 0; j < numberOfItems; j++) {
                                String name = bR.readLine();
                                int quantity = Integer.parseInt(bR.readLine());
                                arrayList.add(new Item(name, quantity));
                            }
                            for (ServerNode serverNode : serverNodeList) {
                                if (serverNode.port == nodeTCPPort || tcpport == nodeTCPPort) {
                                    isOnList = true;
                                    break;
                                }
                            }
                            if (!isOnList) {
                                serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort, arrayList));
                            }
                        }
                        if (gateway == null) {
                            sendUpdatedList();
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
                            int numberOfItems = Integer.parseInt(bR.readLine());
                            ArrayList<Item> arrayList = new ArrayList<>();
                            for (int j = 0; j < numberOfItems; j++) {
                                String name = bR.readLine();
                                int quantity = Integer.parseInt(bR.readLine());
                                arrayList.add(new Item(name, quantity));
                            }
                            for (ServerNode serverNode : serverNodeList) {
                                if (serverNode.port == nodeTCPPort || tcpport == nodeTCPPort) {
                                    isOnList = true;
                                    break;
                                }
                            }
                            if (!isOnList) {
                                serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort, arrayList));
                            }
                        }
                        continue;
                    }

                    if (msg.split(" ")[0].equals("REQUEST")) {
                        String itemm = msg.split(" ")[2];
                        int quantity = Integer.parseInt(msg.split(" ")[1]);
                        String identity = msg.split(" ")[3];
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.identifier.equals(identity)) {
                                for (Item item : serverNode.itemArrayList) {
                                    if (item.name.equals(itemm)) {
                                        item.quantity = quantity;
                                    }
                                }
                            }
                        }
                        continue;
                    }

                    if (msg.equals("GET_ITEM")) {
                        String item = bR.readLine();
                        int quantity = Integer.parseInt(bR.readLine());
                        for (Item itemToCheck : Items) {
                            if (itemToCheck.name.equals(item)) {
                                itemToCheck.quantity -= quantity;
                                sendRequest(itemToCheck.name, itemToCheck.quantity, identifier);
                            }
                        }
                        continue;
                    }

                    if (msg.equals("TERMINATE")) {
                        socket.close();
                        for (ServerNode serverNode : serverNodeList) {
                            try {
                                Socket node = new Socket(serverNode.gateway, serverNode.port);
                                PrintWriter out = new PrintWriter(node.getOutputStream(), true);
                                out.println("TERMINATE");
                            } catch (Exception ignored) {
                            }
                        }
                        System.exit(1);
                    }

                    log(msg);
                    ArrayList<Item> clientItems = new ArrayList<>();
                    for (int i = 1; i < msg.split(" ").length; i++) {
                        clientItems.add(new Item(msg.split(" ")[i].split(":")[0], Integer.parseInt(msg.split(" ")[i].split(":")[1])));
                    }
                    ArrayList<ServerNode> clientNodes = new ArrayList<>();
                    for (Item clientItem : clientItems) {
                        for (Item item : Items) {
                            if (clientItem.name.equals(item.name)) {
                                if (clientItem.quantity >= item.quantity) {
                                    clientItem.quantity -= item.quantity;
                                    clientNodes.add(new ServerNode(identifier, "127.0.0.1", tcpport, new Item(item.name, item.quantity)));
                                } else {
                                    clientNodes.add(new ServerNode(identifier, "127.0.0.1", tcpport, new Item(item.name, clientItem.quantity)));
                                    clientItem.quantity = 0;
                                }
                            }
                        }
                        if (clientItem.quantity > 0) {
                            for (ServerNode serverNode : serverNodeList) {
                                for (Item item : serverNode.itemArrayList) {
                                    if (clientItem.quantity > 0) {
                                        if (clientItem.name.equals(item.name)) {
                                            if (clientItem.quantity >= item.quantity) {
                                                clientItem.quantity -= item.quantity;
                                                clientNodes.add(new ServerNode(serverNode.identifier, serverNode.gateway, serverNode.port, new Item(item.name, item.quantity)));
                                            } else {
                                                clientNodes.add(new ServerNode(serverNode.identifier, serverNode.gateway, serverNode.port, new Item(item.name, clientItem.quantity)));
                                                clientItem.quantity = 0;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    boolean AreAllYes = true;
                    for (Item clientItem : clientItems) {
                        if (clientItem.quantity != 0) {
                            AreAllYes = false;
                            break;
                        }
                    }

                    if (AreAllYes) {
                        for (ServerNode clientNode : clientNodes) {
                            sendMSG(clientNode.oneItem.name + ":" + clientNode.oneItem.quantity + ":"
                                    + clientNode.gateway + ":"
                                    + clientNode.port, bW);
                            if (clientNode.identifier.equals(identifier)) {
                                for (Item item : Items) {
                                    if (clientNode.oneItem.name.equals(item.name)) {
                                        item.quantity -= clientNode.oneItem.quantity;
                                        System.out.println(
                                                "Pozostało " + item.quantity + " zasobu " + item.name);
                                    }
                                }
                            } else {
                                getItem(clientNode, clientNode.oneItem.name, clientNode.oneItem.quantity);
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
                System.out.println(this.getName() + " TCP PROBLEM EXCEPTION");
            }
        }
    }

    //Klasa wątku UDP, pozwala na obsłuchę zapytań innych wątków, służy do synchronizacji oraz wymiany informacji
    private static class UDPServerThread extends Thread {

        byte[] buff = new byte[1460];
        DatagramPacket datagram = new DatagramPacket(buff, buff.length);

        public UDPServerThread() {
        }

        //Funkcja wysyłająca prośbę o aktualizację informacji o zasobach
        synchronized void sendRequest(String item, int quantity, String ident) {
            try {
                for (ServerNode serverNode : serverNodeList) {
                    String message = "REQUEST" + " " + quantity + " " + item + " " + ident;
                    DatagramPacket datagramPacket = new DatagramPacket(message.getBytes(), message.getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Odsyła "Dziecku" informacje o sobie, informuje je o dodaniu do sieci
        synchronized void sendAccepted() {
            try {
                DatagramPacket datagramPacket = new DatagramPacket("ACCEPTED".getBytes(), "ACCEPTED".getBytes().length, InetAddress.getByName(serverNodeList.get(serverNodeList.size() - 1).gateway), serverNodeList.get(serverNodeList.size() - 1).port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(identifier.getBytes(), identifier.getBytes().length, InetAddress.getByName(serverNodeList.get(serverNodeList.size() - 1).gateway), serverNodeList.get(serverNodeList.size() - 1).port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket("127.0.0.1".getBytes(), "127.0.0.1".getBytes().length, InetAddress.getByName(serverNodeList.get(serverNodeList.size() - 1).gateway), serverNodeList.get(serverNodeList.size() - 1).port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(tcpport).getBytes(), String.valueOf(tcpport).getBytes().length, InetAddress.getByName(serverNodeList.get(serverNodeList.size() - 1).gateway), serverNodeList.get(serverNodeList.size() - 1).port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(Items.size()).getBytes(), String.valueOf(Items.size()).getBytes().length, InetAddress.getByName(serverNodeList.get(serverNodeList.size() - 1).gateway), serverNodeList.get(serverNodeList.size() - 1).port);
                datagramSocket.send(datagramPacket);
                for (Item item : Items) {
                    datagramPacket = new DatagramPacket(item.name.getBytes(), item.name.getBytes().length, InetAddress.getByName(serverNodeList.get(serverNodeList.size() - 1).gateway), serverNodeList.get(serverNodeList.size() - 1).port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(item.quantity).getBytes(), String.valueOf(item.quantity).getBytes().length, InetAddress.getByName(serverNodeList.get(serverNodeList.size() - 1).gateway), serverNodeList.get(serverNodeList.size() - 1).port);
                    datagramSocket.send(datagramPacket);
                }
            } catch (Exception ignored) {
            }
        }

        //Funkcja usuwająca ilość posiadanych zasobów danego typu
        synchronized void getItem(ServerNode node, String item, int quantity) {
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

        //Funkcja wysyłająca informację do "Rodzica" o pojawieniu się nowego Node niżej
        synchronized void sendListToParent() {
            try {
                DatagramPacket datagramPacket = new DatagramPacket("NEW_LIST".getBytes(), "NEW_LIST".getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(serverNodeList.toArray().length + 1).getBytes(), String.valueOf(serverNodeList.toArray().length + 1).getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(identifier).getBytes(), String.valueOf(identifier).getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket("127.0.0.1".getBytes(), "127.0.0.1".getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(tcpport).getBytes(), String.valueOf(tcpport).getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                datagramPacket = new DatagramPacket(String.valueOf(Items.size()).getBytes(), String.valueOf(Items.size()).getBytes().length, InetAddress.getByName(gateway), port);
                datagramSocket.send(datagramPacket);
                for (Item item : Items) {
                    datagramPacket = new DatagramPacket(String.valueOf(item.name).getBytes(), String.valueOf(item.name).getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(item.quantity).getBytes(), String.valueOf(item.quantity).getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                }
                for (ServerNode serverNode : serverNodeList) {
                    datagramPacket = new DatagramPacket(serverNode.identifier.getBytes(), serverNode.identifier.getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(serverNode.gateway.getBytes(), serverNode.gateway.getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(serverNode.port).getBytes(), String.valueOf(serverNode.port).getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(Items.size()).getBytes(), String.valueOf(Items.size()).getBytes().length, InetAddress.getByName(gateway), port);
                    datagramSocket.send(datagramPacket);
                    for (Item item : serverNode.itemArrayList) {
                        datagramPacket = new DatagramPacket(String.valueOf(item.name).getBytes(), String.valueOf(item.name).getBytes().length, InetAddress.getByName(gateway), port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(String.valueOf(item.quantity).getBytes(), String.valueOf(item.quantity).getBytes().length, InetAddress.getByName(gateway), port);
                        datagramSocket.send(datagramPacket);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Funkcja wysyłająca do wszystkich aktualnie istniejących Node aktualnej bazy informacji o innych Node'ach
        synchronized void sendUpdatedList() {
            try {
                for (ServerNode serverNode : serverNodeList) {
                    DatagramPacket datagramPacket = new DatagramPacket("UPDATED_LIST".getBytes(), "UPDATED_LIST".getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(serverNodeList.toArray().length + 1).getBytes(), String.valueOf(serverNodeList.toArray().length + 1).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(identifier.getBytes(), identifier.getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket("127.0.0.1".getBytes(), "127.0.0.1".getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(tcpport).getBytes(), String.valueOf(tcpport).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    datagramPacket = new DatagramPacket(String.valueOf(Items.size()).getBytes(), String.valueOf(Items.size()).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                    datagramSocket.send(datagramPacket);
                    for (Item item : Items) {
                        datagramPacket = new DatagramPacket(String.valueOf(item.name).getBytes(), String.valueOf(item.name).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(String.valueOf(item.quantity).getBytes(), String.valueOf(item.quantity).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                    }
                    for (ServerNode serverNode2 : serverNodeList) {
                        datagramPacket = new DatagramPacket(serverNode2.identifier.getBytes(), serverNode2.identifier.getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(serverNode2.gateway.getBytes(), serverNode2.gateway.getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(String.valueOf(serverNode2.port).getBytes(), String.valueOf(serverNode2.port).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(String.valueOf(Items.size()).getBytes(), String.valueOf(Items.size()).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                        datagramSocket.send(datagramPacket);
                        for (Item item : serverNode2.itemArrayList) {
                            datagramPacket = new DatagramPacket(String.valueOf(item.name).getBytes(), String.valueOf(item.name).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                            datagramSocket.send(datagramPacket);
                            datagramPacket = new DatagramPacket(String.valueOf(item.quantity).getBytes(), String.valueOf(item.quantity).getBytes().length, InetAddress.getByName(serverNode.gateway), serverNode.port);
                            datagramSocket.send(datagramPacket);
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //W zależności od parametru -tcpport lub -udpport program rozpoczyna komunikację albo
        // za pomcą TCP albo UDP, ale reszta komunikacji przebiega przy pomocy UDP
        @Override
        public synchronized void start() {
            if (gateway != null) {
                if (isTCP) {
                    try {
                        Socket netSocket = new Socket(gateway, port);
                        PrintWriter out = new PrintWriter(netSocket.getOutputStream(), true);
                        out.println("NEW_NODE");
                        out.println(identifier);
                        out.println(netSocket.getLocalAddress().getHostAddress());
                        out.println(tcpport);
                        out.println(Items.size());
                        for (Item item : Items) {
                            out.println(item.name);
                            out.println(item.quantity);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        DatagramPacket datagramPacket = new DatagramPacket("NEW_NODE".getBytes(), "NEW_NODE".getBytes().length, InetAddress.getByName(gateway), port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(identifier.getBytes(), identifier.getBytes().length, InetAddress.getByName(gateway), port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket("127.0.0.1".getBytes(), "127.0.0.1".getBytes().length, InetAddress.getByName(gateway), port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(String.valueOf(tcpport).getBytes(), String.valueOf(tcpport).getBytes().length, InetAddress.getByName(gateway), port);
                        datagramSocket.send(datagramPacket);
                        datagramPacket = new DatagramPacket(String.valueOf(Items.size()).getBytes(), String.valueOf(Items.size()).getBytes().length, InetAddress.getByName(gateway), port);
                        datagramSocket.send(datagramPacket);
                        for (Item item : Items) {
                            datagramPacket = new DatagramPacket(item.name.getBytes(), item.name.getBytes().length, InetAddress.getByName(gateway), port);
                            datagramSocket.send(datagramPacket);
                            datagramPacket = new DatagramPacket(String.valueOf(item.quantity).getBytes(), String.valueOf(item.quantity).getBytes().length, InetAddress.getByName(gateway), port);
                            datagramSocket.send(datagramPacket);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
            super.start();
        }

        @Override
        public void run() {
            try {
                while (true) {
                    datagramSocket.receive(datagram); //UDP
                    //log(new String(datagram.getData(), 0, datagram.getLength()));

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("NEW_NODE")) {
                        datagramSocket.receive(datagram);
                        String nodeID = new String(datagram.getData(), 0, datagram.getLength());
                        datagramSocket.receive(datagram);
                        String nodeGateway = new String(datagram.getData(), 0, datagram.getLength());
                        datagramSocket.receive(datagram);
                        int nodeTCPPort = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        boolean isOnList = false;
                        datagramSocket.receive(datagram);
                        int numberOfItems = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        ArrayList<Item> arrayList = new ArrayList<>();
                        for (int i = 0; i < numberOfItems; i++) {
                            datagramSocket.receive(datagram);
                            String name = new String(datagram.getData(), 0, datagram.getLength());
                            datagramSocket.receive(datagram);
                            int quantity = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                            arrayList.add(new Item(name, quantity));
                        }
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.port == nodeTCPPort || tcpport == nodeTCPPort) {
                                isOnList = true;
                                break;
                            }
                        }
                        if (!isOnList) {
                            serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort, arrayList));
                        }
                        sendAccepted();
                        if (gateway != null) {
                            sendListToParent();
                        }
                        continue;
                    }

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("ACCEPTED")) {
                        datagramSocket.receive(datagram);
                        String nodeID = new String(datagram.getData(), 0, datagram.getLength());
                        datagramSocket.receive(datagram);
                        String nodeGateway = new String(datagram.getData(), 0, datagram.getLength());
                        datagramSocket.receive(datagram);
                        int nodeTCPPort = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        boolean isOnList = false;
                        datagramSocket.receive(datagram);
                        int numberOfItems = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        ArrayList<Item> arrayList = new ArrayList<>();
                        for (int i = 0; i < numberOfItems; i++) {
                            datagramSocket.receive(datagram);
                            String name = new String(datagram.getData(), 0, datagram.getLength());
                            datagramSocket.receive(datagram);
                            int quantity = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                            arrayList.add(new Item(name, quantity));
                        }
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.port == nodeTCPPort || tcpport == nodeTCPPort) {
                                isOnList = true;
                                break;
                            }
                        }
                        if (!isOnList) {
                            serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort, arrayList));
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
                            datagramSocket.receive(datagram);
                            int numberOfItems = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                            ArrayList<Item> arrayList = new ArrayList<>();
                            for (int j = 0; j < numberOfItems; j++) {
                                datagramSocket.receive(datagram);
                                String name = new String(datagram.getData(), 0, datagram.getLength());
                                datagramSocket.receive(datagram);
                                int quantity = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                                arrayList.add(new Item(name, quantity));
                            }
                            for (ServerNode serverNode : serverNodeList) {
                                if (serverNode.port == nodeTCPPort || tcpport == nodeTCPPort) {
                                    isOnList = true;
                                    break;
                                }
                            }
                            if (!isOnList) {
                                serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort, arrayList));
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
                            datagramSocket.receive(datagram);
                            int numberOfItems = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                            ArrayList<Item> arrayList = new ArrayList<>();
                            for (int j = 0; j < numberOfItems; j++) {
                                datagramSocket.receive(datagram);
                                String name = new String(datagram.getData(), 0, datagram.getLength());
                                datagramSocket.receive(datagram);
                                int quantity = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                                arrayList.add(new Item(name, quantity));
                            }
                            for (ServerNode serverNode : serverNodeList) {
                                if (serverNode.port == nodeTCPPort || tcpport == nodeTCPPort) {
                                    isOnList = true;
                                    break;
                                }
                            }
                            if (!isOnList) {
                                serverNodeList.add(new ServerNode(nodeID, nodeGateway, nodeTCPPort, arrayList));
                            }
                        }
                        continue;
                    }

                    if (new String(datagram.getData(), 0, datagram.getLength()).split(" ")[0].equals("REQUEST")) {
                        int quantity = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()).split(" ")[1]);
                        String itemm = new String(datagram.getData(), 0, datagram.getLength()).split(" ")[2];
                        String identity = new String(datagram.getData(), 0, datagram.getLength()).split(" ")[3];
                        for (ServerNode serverNode : serverNodeList) {
                            if (serverNode.identifier.equals(identity)) {
                                for (Item item : serverNode.itemArrayList) {
                                    if (item.name.equals(itemm)) {
                                        item.quantity = quantity;
                                    }
                                }
                            }
                        }
                        continue;
                    }

                    if (new String(datagram.getData(), 0, datagram.getLength()).equals("GET_ITEM")) {
                        datagramSocket.receive(datagram);
                        String item = new String(datagram.getData(), 0, datagram.getLength());
                        Thread.sleep(10);
                        datagramSocket.receive(datagram);
                        int quantity = Integer.parseInt(new String(datagram.getData(), 0, datagram.getLength()));
                        for (Item itemToCheck : Items) {
                            if (itemToCheck.name.equals(item)) {
                                itemToCheck.quantity -= quantity;
                                sendRequest(itemToCheck.name, itemToCheck.quantity, identifier);
                                System.out.println(
                                        "Pozostało " + itemToCheck.quantity + " zasobu " + itemToCheck.name);
                            }
                        }
                    }

                }
            } catch (IOException | InterruptedException e) {
                System.out.println(this.getName() + " UDP PROBLEM EXCEPTION");
            }
        }
    }
}