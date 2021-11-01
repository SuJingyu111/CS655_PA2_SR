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
    LinkedHashMap<Integer, Packet> flyingBufferA;
    Queue<String> sendingBufferA;
    int nextSeq;
    int windowStart, windowEnd;
    TreeMap<Integer, Message> receiveBufferB;
    int expectedSeq;

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
        String data = message.getData();
        if (flyingBufferA.size() >= WindowSize) {
            sendingBufferA.offer(data);
        }
        else {
            int start = 0;
            while (flyingBufferA.size() < WindowSize) {
                if (!sendingBufferA.isEmpty()) {
                    generateAndSend(sendingBufferA.poll());
                }
                else {
                    if (start < data.length()) {
                        String sendData = data.substring(start, Math.min(start + MAXDATASIZE, data.length()));
                        generateAndSend(sendData);
                        start = Math.min(start + MAXDATASIZE, data.length());
                    }
                    else {
                        break;
                    }
                }
            }
        }
    }

    private void generateAndSend(String data) {
        System.out.println("Sent packet: " + nextSeq);
        int checkSum = getDataCheckSum(data, nextSeq);
        Packet sendPacket = new Packet(nextSeq, 0, checkSum, data);
        toLayer3(A, sendPacket);
        if (flyingBufferA.size() == 0) {
            windowStart = nextSeq;
            windowEnd = nextSeq;
        }
        else {
            windowEnd = nextSeq;
        }
        flyingBufferA.put(nextSeq, sendPacket);
        //stopTimer(A);
        startTimer(A, RxmtInterval);
        nextSeq = (nextSeq + 1) % LimitSeqNo;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        int ackNum = packet.getAcknum(), checkSum = getDataCheckSum(packet.getPayload(), ackNum);
        System.out.println("Receive packet acking: " + ackNum);
        if (checkSum != packet.getChecksum() || flyingBufferA.isEmpty()) {
            return;
        }
        int firstUnackedSeq = flyingBufferA.get(flyingBufferA.keySet().iterator().next()).getSeqnum();
        if (ackNum == firstUnackedSeq) {
            windowStart = ackNum + 1;
            flyingBufferA.remove(ackNum);
            stopTimer(A);
        }
        else if (ackNum > firstUnackedSeq) {
            flyingBufferA.remove(ackNum);
            for (int seq : flyingBufferA.keySet()) {
                if (seq <= ackNum) {
                    flyingBufferA.remove(seq);
                } else {
                    break;
                }
            }
         }
        else {
            flyingBufferA.remove(ackNum);
            Deque<Integer> retransmitQ = new LinkedList<>();
            for (int seq : flyingBufferA.keySet()) {
                retransmitQ.offer(seq);
            }
            for (int i = retransmitQ.size() - 1; i >= 0; i--) {
                int last = retransmitQ.pollLast();
                if (last < ackNum) {
                    retransmitQ.addLast(last);
                    break;
                }
            }
            stopTimer(A);
            startTimer(A, RxmtInterval);
            for (int seq : retransmitQ) {
                toLayer3(A, flyingBufferA.get(seq));
            }
        }
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt()
    {
        //stopTimer(A);
        toLayer3(A, flyingBufferA.get(flyingBufferA.keySet().iterator().next()));
        startTimer(A, RxmtInterval);
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        flyingBufferA = new LinkedHashMap<>();
        sendingBufferA = new LinkedList<>();
        nextSeq = FirstSeqNo;
        windowStart = nextSeq - 1;
        windowEnd = nextSeq - 1;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        int seqNum = packet.getSeqnum();
        String data = packet.getPayload();
        int checkSum = getDataCheckSum(data, seqNum);
        if (checkSum != packet.getChecksum()) {
            //toLayer3(B, new Packet(0, (seqNum + LimitSeqNo - 1) % LimitSeqNo, getDataCheckSum("", seqNum)));
            return;
        }
        if (seqNum == expectedSeq) {
            toLayer5(data);
            toLayer3(B, new Packet(0, seqNum, getDataCheckSum("", seqNum)));
            expectedSeq = (expectedSeq + 1) % LimitSeqNo;
            while (!receiveBufferB.isEmpty() && receiveBufferB.firstKey() == expectedSeq) {
                toLayer5(receiveBufferB.get(expectedSeq).getData());
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

    // Use to print final statistics
    protected void Simulation_done()
    {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + "<YourVariableHere>");
        System.out.println("Number of retransmissions by A:" + "<YourVariableHere>");
        System.out.println("Number of data packets delivered to layer 5 at B:" + "<YourVariableHere>");
        System.out.println("Number of ACK packets sent by B:" + "<YourVariableHere>");
        System.out.println("Number of corrupted packets:" + "<YourVariableHere>");
        System.out.println("Ratio of lost packets:" + "<YourVariableHere>" );
        System.out.println("Ratio of corrupted packets:" + "<YourVariableHere>");
        System.out.println("Average RTT:" + "<YourVariableHere>");
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

}