import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)
    //Variables that I added
    LinkedList<Packet> flyingBufferA;
    Queue<String> sendingBufferA;
    int nextSeq;
    int windowStart, windowEnd;
    TreeMap<Integer, Message> receiveBufferB;
    int expectedSeq;
    boolean timerOn;

    private int getDataCheckSum(String data, int number) {
        int checkSum = 0;
        for (int i = data.length() - 1; i >= 0; i--) {
            checkSum += data.charAt(i);
        }
        return checkSum + number;
    }

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }


    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {
        receivedFromLayer5A++;
        String data = message.getData();
        if (flyingBufferA.size() >= WindowSize) {
            sendingBufferA.offer(data);
        }
        else {
            int start = 0;
            while (flyingBufferA.size() < WindowSize) {
                if (!sendingBufferA.isEmpty()) {
                    origTransmitCntA++;
                    generateAndSend(sendingBufferA.poll());
                }
                else {
                    if (start < data.length()) {
                        String sendData = data.substring(start, Math.min(start + MAXDATASIZE, data.length()));
                        origTransmitCntA++;
                        generateAndSend(sendData);
                        start = Math.min(start + MAXDATASIZE, data.length());
                    }
                    else {
                        break;
                    }
                }
            }
            while (start < data.length()) {
                sendingBufferA.offer(data.substring(start, Math.min(start + MAXDATASIZE, data.length())));
                start = Math.min(start + MAXDATASIZE, data.length());
            }
        }
        storedInBuffer = sendingBufferA.size();
    }

    private void generateAndSend(String data) {
        System.out.println("Sent packet: " + nextSeq);
        int checkSum = getDataCheckSum(data, nextSeq);
        Packet sendPacket = new Packet(nextSeq, 0, checkSum, data);
        if (flyingBufferA.size() == 0) {
            windowStart = nextSeq;
            windowEnd = nextSeq;
        }
        else {
            windowEnd = nextSeq;
        }
        flyingBufferA.add(sendPacket);
        //if (timerOn) {
            stopTimer(A);
        //}
        startTimer(A, RxmtInterval);
        //timerOn = true;
        toLayer3(A, sendPacket);
        updateSeqNum();
    }

    private void updateSeqNum() {
        nextSeq = (nextSeq + 1) % LimitSeqNo;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        ackReceivedA++;
        int ackNum = packet.getAcknum(), checkSum = getDataCheckSum(packet.getPayload(), ackNum);
        if (checkSum != packet.getChecksum() || flyingBufferA.isEmpty()) {
            return;
        }
        System.out.println("Packet " + ackNum + " acked");
        int firstUnackedSeq = flyingBufferA.get(0).getSeqnum();
        if (ackNum == firstUnackedSeq) {
            windowStart = (windowStart + 1) % LimitSeqNo;
            flyingBufferA.remove(0);
            stopTimer(A);
            if (!flyingBufferA.isEmpty()) {
                startTimer(A ,RxmtInterval);
            }
            //timerOn = false;
        }
        else {
            reTransmitCntA++;
            toLayer3(A, flyingBufferA.get(0));
            //if (timerOn) {
                stopTimer(A);
            //}
            startTimer(A, RxmtInterval);
            //timerOn = true;
        }
        while (flyingBufferA.size() < WindowSize) {
            if (!sendingBufferA.isEmpty()) {
                origTransmitCntA++;
                Packet newPacket = generatePacket(sendingBufferA.poll());
                flyingBufferA.add(newPacket);
                toLayer3(A, newPacket);
                //if (timerOn) {
                    stopTimer(A);
                //}
                startTimer(A, RxmtInterval);
                //timerOn = true;
            }
            else {
                break;
            }
        }
    }

    public Packet generatePacket(String data) {
        Packet newPacket = new Packet(nextSeq, 0, getDataCheckSum(data, nextSeq), data);
        updateSeqNum();
        return newPacket;
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt()
    {
        timeOutCnt++;
        System.out.println("time out!");
        stopTimer(A);
        startTimer(A, RxmtInterval);
        timerOn = true;
        reTransmitCntA++;
        toLayer3(A, flyingBufferA.get(0));
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        flyingBufferA = new LinkedList<>();
        sendingBufferA = new LinkedList<>();
        nextSeq = FirstSeqNo;
        windowStart = nextSeq - 1;
        windowEnd = nextSeq - 1;
        timerOn = false;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        packetReceivedB++;
        int seqNum = packet.getSeqnum();
        String data = packet.getPayload();
        int checkSum = getDataCheckSum(data, seqNum);
        if (checkSum != packet.getChecksum()) {
            corruptedCnt++;
            ackSentCntB++;
            toLayer3(B, new Packet(0, (expectedSeq + LimitSeqNo - 1) % LimitSeqNo, getDataCheckSum("", (expectedSeq + LimitSeqNo - 1) % LimitSeqNo)));
            return;
        }
        if (seqNum == expectedSeq) {
            delieveredCntB++;
            toLayer5(data);
            System.out.println("Receive packet " + seqNum);
            ackSentCntB++;
            toLayer3(B, new Packet(0, seqNum, getDataCheckSum("", seqNum)));
            expectedSeq = (expectedSeq + 1) % LimitSeqNo;
            while (!receiveBufferB.isEmpty() && receiveBufferB.firstKey() == expectedSeq) {
                delieveredCntB++;
                toLayer5(receiveBufferB.get(expectedSeq).getData());
                System.out.println("Receive packet " + expectedSeq);
                ackSentCntB++;
                toLayer3(B, new Packet(0, expectedSeq, getDataCheckSum("", seqNum)));
                receiveBufferB.remove(expectedSeq);
                expectedSeq = (expectedSeq + 1) % LimitSeqNo;
            }
        }
        else {
            receiveBufferB.put(seqNum, new Message(data));
        }
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        receiveBufferB = new TreeMap<>();
        expectedSeq = FirstSeqNo;
    }

    //Stat Variables
    int origTransmitCntA = 0, reTransmitCntA = 0, ackReceivedA;
    int packetReceivedB, delieveredCntB = 0, ackSentCntB = 0;
    int corruptedCnt = 0;
    int timeOutCnt = 0;

    //Debugging Variables
    int receivedFromLayer5A = 0;
    int storedInBuffer = 0;

    // Use to print final statistics
    protected void Simulation_done()
    {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + origTransmitCntA);
        System.out.println("Number of retransmissions by A:" + reTransmitCntA);
        System.out.println("Number of data packets delivered to layer 5 at B:" + delieveredCntB);
        System.out.println("Number of ACK packets sent by B:" + ackSentCntB);
        System.out.println("Number of corrupted packets:" + corruptedCnt);
        System.out.println("Ratio of lost packets:" + (1 - (double)(ackReceivedA + packetReceivedB) / (origTransmitCntA + reTransmitCntA + ackSentCntB)));
        System.out.println("Ratio of corrupted packets:" + (double)corruptedCnt / (origTransmitCntA + reTransmitCntA + ackSentCntB));
        System.out.println("Average RTT:" + "<YourVariableHere>");
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
        System.out.println("Number of timeouts on A:" + timeOutCnt);
        System.out.println("Number of packets received on A:" + receivedFromLayer5A);
        System.out.println("Number of packets stored in sending buffer on A:" + storedInBuffer);
        System.out.println("Number of packets received on B:" + packetReceivedB);
    }

}