package Servers;

import Utils.*;
import org.omg.CORBA.DCMS;
import org.omg.CORBA.DCMSHelper;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.sleep;

public class ReplicaManager implements Runnable {
    private Config.ARCHITECTURE.REPLICAS replicaManagerID;
    private int fromFrontEndPort;
    private int toFrontEndPort;
    private int fromLeaderPort;
    private int leaderPort;
    private int heartBeatPort;
    private boolean isLeader;
    private ORB orb;
    private org.omg.CORBA.Object namingContextObj;
    private NamingContextExt namingContextRef;
    private FIFO fifo;
    private Config.ARCHITECTURE.REPLICAS leaderID;
    private Map<Integer, Integer> acknowledgementMap;

    public ReplicaManager(Config.ARCHITECTURE.REPLICAS replicaManagerID) {
        try {
            this.replicaManagerID = replicaManagerID;
            this.acknowledgementMap = Collections.synchronizedMap(new HashMap<Integer, Integer>());
            switch (replicaManagerID) {
                case KEN_RO:
                    isLeader = false;
                    this.fromFrontEndPort = Config.ARCHITECTURE.REPLICAS.KEN_RO.getValue() * Config.UDP.PORT_FRONT_END_TO_LEADER;
                    this.toFrontEndPort = Config.ARCHITECTURE.REPLICAS.KEN_RO.getValue() * Config.UDP.PORT_LEADER_TO_FRONT_END;
                    this.fromLeaderPort = Config.ARCHITECTURE.REPLICAS.KEN_RO.getValue() * Config.UDP.PORT_LEADER_TO_BACKUPS;
                    this.leaderPort = Config.ARCHITECTURE.REPLICAS.KEN_RO.getValue() * Config.UDP.PORT_BACKUPS_TO_LEADER;
                    this.heartBeatPort = Config.ARCHITECTURE.REPLICAS.KEN_RO.getValue() * Config.UDP.PORT_HEART_BEAT;
                    break;
                case KAMAL:
                    isLeader = false;
                    this.fromFrontEndPort = Config.ARCHITECTURE.REPLICAS.KAMAL.getValue() * Config.UDP.PORT_FRONT_END_TO_LEADER;
                    this.toFrontEndPort = Config.ARCHITECTURE.REPLICAS.KAMAL.getValue() * Config.UDP.PORT_LEADER_TO_FRONT_END;
                    this.fromLeaderPort = Config.ARCHITECTURE.REPLICAS.KAMAL.getValue() * Config.UDP.PORT_LEADER_TO_BACKUPS;
                    this.leaderPort = Config.ARCHITECTURE.REPLICAS.KAMAL.getValue() * Config.UDP.PORT_BACKUPS_TO_LEADER;
                    this.heartBeatPort = Config.ARCHITECTURE.REPLICAS.KAMAL.getValue() * Config.UDP.PORT_HEART_BEAT;
                    break;
                case MINH:
                    isLeader = true;
                    this.fromFrontEndPort = Config.ARCHITECTURE.REPLICAS.MINH.getValue() * Config.UDP.PORT_FRONT_END_TO_LEADER;
                    this.toFrontEndPort = Config.ARCHITECTURE.REPLICAS.MINH.getValue() * Config.UDP.PORT_LEADER_TO_FRONT_END;
                    this.fromLeaderPort = Config.ARCHITECTURE.REPLICAS.MINH.getValue() * Config.UDP.PORT_LEADER_TO_BACKUPS;
                    this.leaderPort = Config.ARCHITECTURE.REPLICAS.MINH.getValue() * Config.UDP.PORT_BACKUPS_TO_LEADER;
                    this.heartBeatPort = Config.ARCHITECTURE.REPLICAS.MINH.getValue() * Config.UDP.PORT_HEART_BEAT;
                    break;
                default:
                    // Do nothing
                    break;
            }
            fifo = new FIFO();
            prepareORB();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    @Override
    public void run() {
        // Start all the server
        switch (replicaManagerID) {
            case MINH:
                startMinhReplica();
                break;
            case KAMAL:
                startKamalReplica();
                break;
            case KEN_RO:
                startKenroReplica();
                break;
            default:
                // Do nothing
                break;
        }

        if (isLeader) {
            // If this is leader, listen to FrontEndImpl
            new Thread(new Runnable() {
                @Override
                public void run() {
                    listenToFrontEnd();
                }
            }).start();

            // and to Backups
            new Thread(new Runnable() {
                @Override
                public void run() {
                    listenToAcknowledgements();
                }
            }).start();
        } else { // If this is backup, listen to Leader
            new Thread(new Runnable() {
                @Override
                public void run() {
                    listenToLeader();
                }
            }).start();
        }
    }

    // Getters & Setters
    public Config.ARCHITECTURE.REPLICAS getReplicaManagerID() {
        return replicaManagerID;
    }

    public int getFromFrontEndPort() {
        return fromFrontEndPort;
    }

    public int getFromLeaderPort() {
        return fromLeaderPort;
    }

    public int getHeartBeatPort() {
        return heartBeatPort;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public void setLeader(boolean leader) {
        isLeader = leader;
    }

    public void updateLeaderInfo(Config.ARCHITECTURE.REPLICAS newLeaderID) {
        this.leaderID = newLeaderID;
    }

    //Helper functions
    private void startMinhReplica() {
        try {
            // Initiate local ORB object
            ORB orb = ORB.init(Config.CORBA.ORB_PARAMETERS.split(" "), null);

            // Get reference to RootPOA and get POAManager
            POA rootPOA = POAHelper.narrow(orb.resolve_initial_references(Config.CORBA.ROOT_POA));
            rootPOA.the_POAManager().activate();

            // Create servant and register it with the ORB
            CenterServer mtlServer = new CenterServer(Configuration.Server_ID.MTL);
            mtlServer.setORB(orb);
            CenterServer lvlServer = new CenterServer(Configuration.Server_ID.LVL);
            lvlServer.setORB(orb);
            CenterServer ddoServer = new CenterServer(Configuration.Server_ID.DDO);
            ddoServer.setORB(orb);

            // Get object reference from the servant
            org.omg.CORBA.Object mtlRef = rootPOA.servant_to_reference(mtlServer);
            org.omg.CORBA.Object lvlRef = rootPOA.servant_to_reference(lvlServer);
            org.omg.CORBA.Object ddoRef = rootPOA.servant_to_reference(ddoServer);
            DCMS mtlDcmsServer = DCMSHelper.narrow(mtlRef);
            DCMS lvlDcmsServer = DCMSHelper.narrow(lvlRef);
            DCMS ddoDcmsServer = DCMSHelper.narrow(ddoRef);

            // Get the root Naming Context
            org.omg.CORBA.Object objRef = orb.resolve_initial_references(Config.CORBA.NAME_SERVICE);
            NamingContextExt namingContextRef = NamingContextExtHelper.narrow(objRef);

            // Bind the object reference to the Naming Context
            NameComponent path[] = namingContextRef.to_name(Configuration.Server_ID.MTL.name());
            namingContextRef.rebind(path, mtlDcmsServer);
            path = namingContextRef.to_name(Configuration.Server_ID.LVL.name());
            namingContextRef.rebind(path, mtlDcmsServer);
            path = namingContextRef.to_name(Configuration.Server_ID.DDO.name());
            namingContextRef.rebind(path, mtlDcmsServer);

            // Run the server
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mtlDcmsServer.startUDPServer();
                }
            }).start();
            System.out.println("Server " + Configuration.Server_ID.MTL.name() + " is running ...");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    lvlDcmsServer.startUDPServer();
                }
            }).start();
            System.out.println("Server " + Configuration.Server_ID.LVL.name() + " is running ...");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    ddoDcmsServer.startUDPServer();
                }
            }).start();
            System.out.println("Server " + Configuration.Server_ID.DDO.name() + " is running ...");

            orb.run();
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace(System.out);
        }
    }

    private void startKamalReplica() {
    }

    private void startKenroReplica() {
    }

    private void listenToFrontEnd() {
        DatagramSocket fromFrontEndSocket = null;
        try {
            fromFrontEndSocket = new DatagramSocket(fromFrontEndPort);

            while (true) {
                byte[] buffer = new byte[1000];

                // Get the request
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                fromFrontEndSocket.receive(requestPacket);

                // Each request will be handled by a thread to improve performance
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Rebuild the request object
                        Request request = Config.deserializeRequest(requestPacket.getData());
                        String managerID = request.getManagerID();
                        Response response;
                        if (request.getSequenceNumber() == fifo.getExpectedRequestNumber(managerID)) { // This request is the one expected
                            // Add the request to the queue, so it can be executed in the next step
                            fifo.holdRequest(managerID, request);

                            // Execute this request and the continuous chain of requests after it hold in the queue
                            while (true) {
                                Request currentRequest = fifo.popNextRequest(managerID);

                                // Increase the expected sequence number by 1
                                fifo.generateRequestNumber(managerID);

                                // Handle the request
                                response = executeRequest(currentRequest);

                                /**
                                 * Only broadcast requests if the leader executes the request successfully
                                 * If the leader succeeds, the response to client will be successful
                                 * As long as a RM can proceed the request, clients still get the successful result
                                 * Leader waits for acknowledgements from both backups then answers FrontEndImpl
                                 */
                                // Send the result to backups & wait for acknowledgements
                                if (response.isSuccess()) {
                                    // Broadcast using FIFO multicast
                                    broadcastToGroup(currentRequest);

                                    // Check backups' acknowledgement
                                    waitForEnoughAcknowledgement(currentRequest);
                                }

                                // Response to FrontEndImpl
                                responseToFrontEnd(response);

                                // If the next request on hold doesn't have the expected sequence number, the loop will end
                                if (fifo.peekFirstRequestHoldNumber(managerID) != fifo.getExpectedRequestNumber(managerID))
                                    break;
                            }
                        } else if (request.getSequenceNumber() > fifo.getExpectedRequestNumber(managerID)) { // There're other requests must come before this request
                            // Save the request to the holdback queue
                            fifo.holdRequest(managerID, request);

                            /**
                             * How to take care of the situation
                             * When the request is put to the queue
                             * But will never be execute until a new request is sent???
                             */
                        } // Else the request is duplicated, ignore it
                    }
                }).start();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            if (fromFrontEndSocket != null)
                fromFrontEndSocket.close();
        }
    }

    private void responseToFrontEnd(Response response) {
        DatagramSocket toFrontEndSocket = null;
        try {
            toFrontEndSocket = new DatagramSocket(toFrontEndPort);
            String managerID = response.getManagerID();

            if (response.getSequenceNumber() == fifo.getExpectedResponseNumber(managerID)) { // This response is the one expected
                // Add the response to the queue, so it can be forwarded in the next step
                fifo.holdResponse(managerID, response);

                // Forward this response and the continuous chain of other responses after it hold in the queue
                while (true) {
                    Response currentResponse = fifo.popNextResponse(managerID);

                    // Increase the expected sequence number by 1
                    fifo.generateResponseNumber(managerID);

                    // Forward the response using FIFO's reliable unicast

                    // If the next response on hold doesn't have the expected sequence number, the loop will end
                    if (fifo.peekFirstResponseHoldNumber(managerID) != fifo.getExpectedResponseNumber(managerID))
                        break;
                }
            } else if (response.getSequenceNumber() > fifo.getExpectedResponseNumber(managerID)) { // There's other responses must come before this response
                // Save the response to the holdback queue
                fifo.holdResponse(managerID, response);

                /**
                 * How to take care of the situation
                 * When the response is put to the queue
                 * But will never be forwarded until a new response is sent???
                 */
            } // Else the response is duplicated, ignore it
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            if (toFrontEndSocket != null)
                toFrontEndSocket.close();
        }
    }

    private Response executeRequest(Request request) {
        Response response = null;
        try {
            String managerID = request.getManagerID();
            Config.ARCHITECTURE.SERVER_ID serverID = Config.ARCHITECTURE.SERVER_ID.valueOf(managerID.substring(0, 3).toUpperCase());

            // Pass the NameComponent to the NamingService to get the object, then narrow it to proper type
            DCMS dcmsServer = DCMSHelper.narrow(namingContextRef.resolve_str(serverID.name()));
            String result = "";
            boolean isSuccess = false;
            switch (request.getMethodName()) {
                case createTRecord: {
                    String createdRecordID = dcmsServer.createTRecord(managerID, request.getFirstName(), request.getLastName(), request.getAddress(), request.getPhone(), request.getSpecialization(), request.getLocation());
                    if (createdRecordID.compareTo("") != 0) {
                        result = String.format(Config.RESPONSE.CREATE_T_RECORD, createdRecordID);
                        isSuccess = true;
                    }
                    break;
                }

                case createSRecord: {
                    String createdRecordID = dcmsServer.createSRecord(managerID, request.getFirstName(), request.getLastName(), request.getCoursesRegistered(), request.getStatus());
                    if (createdRecordID.compareTo("") != 0) {
                        result = String.format(Config.RESPONSE.CREATE_S_RECORD, createdRecordID);
                        isSuccess = true;
                    }
                    break;
                }

                case getRecordsCount: {
                    result = dcmsServer.getRecordCounts(managerID);
                    if (result.compareTo("") != 0)
                        isSuccess = true;
                    break;
                }

                case editRecord: {
                    isSuccess = dcmsServer.editRecord(managerID, request.getRecordID(), request.getFieldName(), request.getNewValue());
                    if (isSuccess)
                        result = String.format(Config.RESPONSE.EDIT_RECORD, request.getRecordID());
                    break;
                }

                case transferRecord: {
                    isSuccess = dcmsServer.transferRecord(managerID, request.getRecordID(), request.getRemoteCenterServerName());
                    if (isSuccess)
                        result = String.format(Config.RESPONSE.TRANSFER_RECORD, request.getRecordID());
                    break;
                }

                case printRecord: {
                    result = dcmsServer.printRecord(managerID, request.getRecordID());
                    if (result.compareTo("") != 0)
                        isSuccess = true;
                    break;
                }

                case printAllRecords: {
                    result = dcmsServer.printAllRecords(managerID);
                    if (result.compareTo("") != 0)
                        isSuccess = true;
                    break;
                }

                default: {
                    // Do nothing
                    break;
                }
            }
            response = new Response(request, isSuccess, result);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return response;
    }

    private void listenToLeader() {
        DatagramSocket leaderSocket = null;
        try {
            leaderSocket = new DatagramSocket(fromLeaderPort);

            while (true) {
                byte[] buffer = new byte[1000];

                // Get the request
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                leaderSocket.receive(requestPacket);

                // Each request will be handled by a thread to improve performance
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        /**
                         * To improve performance, backups will send acknowledgement to the leader
                         * Right when it receive the message, don't need to wait for the processing
                         */
                        // Rebuild the request object
                        Request request = Config.deserializeRequest(requestPacket.getData());
                        String managerID = request.getManagerID();

                        // Send acknowledgement to the leader
                        acknowledgeToLeader(request);

                        // This request is the one expected
                        if (request.getSequenceNumber() == fifo.getExpectedRequestNumber(managerID)) {
                            // Re-broadcast the request to the group
                            broadcastToGroup(request);

                            // Add the request to the queue, so it can be executed in the next step
                            fifo.holdRequest(managerID, request);

                            // Execute this request and the continuous chain of requests after it hold in the queue
                            while (true) {
                                Request currentRequest = fifo.popNextRequest(managerID);

                                // Increase the expected sequence number by 1
                                fifo.generateRequestNumber(managerID);

                                // Handle the request
                                executeRequest(currentRequest);

                                // If the next request on hold doesn't have the expected sequence number, the loop will end
                                if (fifo.peekFirstRequestHoldNumber(managerID) != fifo.getExpectedRequestNumber(managerID))
                                    break;
                            }
                        }
                        // There're other requests must come before this request
                        else if (request.getSequenceNumber() > fifo.getExpectedRequestNumber(managerID)) {
                            // Re-broadcast the request to the group
                            broadcastToGroup(request);

                            // Save the request to the holdback queue
                            fifo.holdRequest(managerID, request);

                            /**
                             * How to take care of the situation
                             * When the request is put to the queue
                             * But will never be execute until a new request is sent???
                             */
                        } // Else the request is duplicated, ignore it
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        } finally {
            if (leaderSocket != null)
                leaderSocket.close();
        }
    }

    private void acknowledgeToLeader(Request request) {
        Unicast unicast = null;
        try {
            int leaderPort = leaderID.getValue() * Config.UDP.PORT_BACKUPS_TO_LEADER;
            unicast = new Unicast(leaderPort);
            unicast.send(String.valueOf(request.getSequenceNumber()).getBytes());
        } catch (SocketException e) {
            e.printStackTrace();
        } finally {
            if (unicast != null)
                unicast.closeSocket();
        }
    }

    private void listenToAcknowledgements() {
        DatagramSocket socket = null;
        while (true) {
            try {
                byte[] buffer = new byte[50];
                DatagramPacket acknowledgement = new DatagramPacket(buffer, buffer.length);
                int listeningPort = replicaManagerID.getValue() * Config.UDP.PORT_BACKUPS_TO_LEADER;
                socket = new DatagramSocket(listeningPort);
                socket.receive(acknowledgement);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int sequenceNumber = Integer.valueOf(new String(acknowledgement.getData()).trim());
                        synchronized (acknowledgementMap) {
                            int noOfAck = acknowledgementMap.getOrDefault(sequenceNumber, 0);
                            if (noOfAck != 0)
                                acknowledgementMap.put(sequenceNumber, ++noOfAck);
                        }
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace(System.out);
            } finally {
                if (socket != null)
                    socket.close();
            }
        }
//        try {
//            for (int i = 0; i < Config.ARCHITECTURE.REPLICAS.values().length - 1; i++) {
//                byte[] buffer = new byte[50];
//                DatagramPacket acknowledgePacket = new DatagramPacket(buffer, buffer.length);
//                socket.receive(acknowledgePacket);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    private void prepareORB() throws Exception {
        // Initiate client ORB
        orb = ORB.init(Config.CORBA.ORB_PARAMETERS.split(" "), null);

        // Get object reference to the Naming Service
        namingContextObj = orb.resolve_initial_references(Config.CORBA.NAME_SERVICE);

        // Narrow the NamingContext object reference to the proper type to be usable (like any CORBA object)
        namingContextRef = NamingContextExtHelper.narrow(namingContextObj);
    }

    private void broadcastToGroup(Request request) {
        // Prepare the list of Backups
        ArrayList<Integer> ports = new ArrayList<>();
        for (Config.ARCHITECTURE.REPLICAS replica : Config.ARCHITECTURE.REPLICAS.values()) {
            if (replica != this.replicaManagerID)
                ports.add(replica.getValue() * Config.UDP.PORT_LEADER_TO_BACKUPS);
        }

        // Broadcast using FIFO multicast
        fifo.multiCast(ports, request);
    }

    private void waitForEnoughAcknowledgement(Request request) {
        int sequenceNumber = request.getSequenceNumber();
        while (true) {
            try {
                synchronized (acknowledgementMap) {
                    int noOfAck = acknowledgementMap.getOrDefault(sequenceNumber, 0);
                    if (noOfAck == 2)
                        break;
                }
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
            }
        }
    }
}
