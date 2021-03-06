package Servers;

import Utils.Config;
import com.sun.org.apache.regexp.internal.RE;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.*;

import static java.lang.Thread.sleep;

class Election {
    private Config.ARCHITECTURE.REPLICAS replicaManagerID = null;
    private int noOfAliveRM;
    private static final Object noOfAliveRMLock = new Object();
    private Config.ARCHITECTURE.REPLICAS currentLeader;

    public Election(Config.ARCHITECTURE.REPLICAS replicaManagerID) throws SocketException {
        this.replicaManagerID = replicaManagerID;

        new Thread(() -> {
            listenToElectionMessage();
        }).start();
    }

    public Config.ARCHITECTURE.REPLICAS startElection(boolean[] replicasStatus) {
        try {
            this.noOfAliveRM = 1;
            currentLeader = this.replicaManagerID;

            for (Config.ARCHITECTURE.REPLICAS replicaID : Config.ARCHITECTURE.REPLICAS.values()) {
                if (replicaID != this.replicaManagerID)
                    checkAlive(replicaID, replicasStatus);
            }
            // Wait for responses from all alive replicas
            sleep(Config.ELECTION.ELECTION_TIMEOUT);
            for (Config.ARCHITECTURE.REPLICAS replicaID : Config.ARCHITECTURE.REPLICAS.values()) {
                if (replicasStatus[replicaID.getCoefficient() - 1])
                    currentLeader = replicaID;
            }
        } catch (InterruptedException e) {
            e.printStackTrace(System.err);
        }
        return currentLeader;
    }

    private void checkAlive(Config.ARCHITECTURE.REPLICAS replicaID, boolean[] replicasStatus) {
//        new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                sendElectionMessage(socket, replicaID);
                boolean isReplicaAlive = listenToElectionAnswer(socket);
                if (isReplicaAlive) {
                    synchronized (noOfAliveRMLock) {
                        noOfAliveRM++;
                    }
                }
                synchronized (replicasStatus) {
                    replicasStatus[replicaID.getCoefficient() - 1] = isReplicaAlive;
                }
            } catch (SocketException e) {
                e.printStackTrace(System.err);
            } finally {
                if (socket != null)
                    socket.close();
                return;
            }
//        }).start();
    }

    private void sendElectionMessage(DatagramSocket socket, Config.ARCHITECTURE.REPLICAS toReplicaID) {
        try {
            byte[] buffer = Config.ELECTION.MESSAGE.getBytes();
            DatagramPacket electionPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getLocalHost(), toReplicaID.getCoefficient() * Config.UDP.PORT_ELECTION);
            socket.send(electionPacket);
            System.out.println(this.replicaManagerID.name() + " send election message to " + toReplicaID.name() + " from port " + socket.getLocalPort() + " to port " + toReplicaID.getCoefficient() * Config.UDP.PORT_ELECTION);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private boolean listenToElectionAnswer(DatagramSocket socket) {
        if (socket != null) {
            byte[] buffer = new byte[1000];
            DatagramPacket answerPacket = new DatagramPacket(buffer, buffer.length);
            try {
                socket.setSoTimeout(Config.ELECTION.ANSWER_TIMEOUT);
                socket.receive(answerPacket);
                String answerContent = new String(answerPacket.getData()).trim();
                if (answerContent.compareTo(Config.ELECTION.RESPONSE) == 0) {
//                    System.out.println(this.replicaManagerID.name() + " answers to election message at port " + socket.getLocalPort());
                    return true;
                } else {
//                    System.out.println("Replica NOT answers to election message " + answerContent);
                    return false;
                }
            } catch (SocketTimeoutException e) {
                System.err.println(this.replicaManagerID.name() + " don't get answer at port " + socket.getLocalPort());
//                e.printStackTrace(System.err);
                return false;
            } catch (Exception e) {
                e.printStackTrace(System.err);
                return false;
            } finally {
                socket.close();
            }
        } else
            return false;
    }

    private void listenToElectionMessage() {
        try {
            DatagramSocket listeningSocket = new DatagramSocket(replicaManagerID.getCoefficient() * Config.UDP.PORT_ELECTION);
            while (true) {
                byte[] receiveBuffer = new byte[1000];
                DatagramPacket electionPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                System.out.println(replicaManagerID.name() + " listen to election message at port " + listeningSocket.getLocalPort());
                listeningSocket.receive(electionPacket);
//                System.out.println(this.replicaManagerID.name() + " get the election message");
                new Thread(() -> {
                    String receiveContent = new String(electionPacket.getData()).trim();
                    DatagramSocket answerSocket = null;
                    try {
                        answerSocket = new DatagramSocket();
                        if (receiveContent.compareTo(Config.ELECTION.MESSAGE) == 0) {
                            byte[] sendBuffer = Config.ELECTION.RESPONSE.getBytes();
                            DatagramPacket sendingPacket = new DatagramPacket(sendBuffer, sendBuffer.length, InetAddress.getLocalHost(), electionPacket.getPort());
                            answerSocket.send(sendingPacket);
                            System.out.println(this.replicaManagerID.name() + " response to the election message to port " + sendingPacket.getPort());
                        }
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    } finally {
                        if (answerSocket != null)
                            answerSocket.close();
                        return;
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    public void announceNewLeader() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            String announceContent = String.format(Config.ELECTION.ANNOUNCEMENT, noOfAliveRM, currentLeader.name());
            byte[] buffer = announceContent.getBytes();
            for (Config.ARCHITECTURE.REPLICAS replicaID : Config.ARCHITECTURE.REPLICAS.values()) {
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getLocalHost(), replicaID.getCoefficient() * Config.UDP.PORT_NEW_LEADER);
                socket.send(datagramPacket);
//                System.out.println(this.replicaManagerID.name() + " announce " + noOfAliveRM + " RM alive, new leader " + currentLeader.name() + " to " + replicaID.name());
            }
            DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getLocalHost(), Config.FRONT_END.COEFFICIENT * Config.UDP.PORT_NEW_LEADER);
            socket.send(datagramPacket);
            System.out.println(this.replicaManagerID.name() + " announce " + noOfAliveRM + " RM alive, new leader " + currentLeader.name() + " to everyone");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            if (socket != null)
                socket.close();
        }
    }
}