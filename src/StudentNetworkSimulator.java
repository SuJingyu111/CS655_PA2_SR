import java.util.*;

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
    private Queue<Packet> sendingBuffer;
    private LinkedHashMap<Integer, Packet> flyingBufferA;
    private HashMap<Integer, Packet> rcvBufferB;

    private int nextSeq;
    //private int sendWindowStart;
    boolean timerOn;

    private int expectedSeqNum;

    private boolean checkCheckSum(int result, String payload) {
        return getCheckSum(payload) == result;
    }

    private int getCheckSum(String payload) {
        int checkSum = 0;
        for (int i = 0; i < payload.length(); i++) {
            checkSum += payload.charAt(i);
        }
        return checkSum;
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
        receivedCntA++;
        int seqNum = nextSeq;
        int ackNum = -1;
        String payload = message.getData();
        int checksum = getCheckSum(payload);
        Packet packet = new Packet(seqNum, ackNum, checksum, payload);
        nextSeq++;
        if (flyingBufferA.size() >= WindowSize) {
            sendingBuffer.offer(packet);
        }
        else{
            while (flyingBufferA.size() < WindowSize && !sendingBuffer.isEmpty()) {
                Packet prevPkt = sendingBuffer.poll();
                origTransmittedCntA++;
                if (timerOn) {
                    stopTimer(A);
                }
                toLayer3(A, prevPkt);
                startTimer(A, RxmtInterval);
                timerOn = true;
                flyingBufferA.put(prevPkt.getSeqnum(), prevPkt);
            }
            if (flyingBufferA.size() < WindowSize) {
                origTransmittedCntA++;
                if (timerOn) {
                    stopTimer(A);
                }
                toLayer3(A, packet);
                startTimer(A, RxmtInterval);
                timerOn = true;
                flyingBufferA.put(seqNum, packet);
            }
            else {
                sendingBuffer.offer(packet);
            }
        }
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        int checksum = packet.getChecksum();
        int ackNum = packet.getAcknum();

        if (checkCheckSum(checksum, packet.getPayload())){

            Set<Integer> seqNumbers = new HashSet<>(flyingBufferA.keySet());
            for (int seqNum : seqNumbers) {
                if (seqNum > ackNum) {
                    break;
                }
                //sendWindowStart = seqNum + 1;
                flyingBufferA.remove(seqNum);
            }
            //sendWindowStart = seqNumIt.hasNext() ? seqNumIt.next() : sendWindowStart;

            while (flyingBufferA.size() < WindowSize && !sendingBuffer.isEmpty()) {
                origTransmittedCntA++;
                Packet retransPkt = sendingBuffer.poll();
                if (timerOn) {
                    stopTimer(A);
                }
                toLayer3(A, retransPkt);
                startTimer(A, RxmtInterval);
                timerOn = true;
                flyingBufferA.put(retransPkt.getSeqnum(), retransPkt);
            }
        }
        else{
            if (!flyingBufferA.isEmpty()) {
                retransmitNum++;
                corruptedNum++;
                if (timerOn) {
                    stopTimer(A);
                }
                toLayer3(A, flyingBufferA.get(flyingBufferA.keySet().iterator().next()));
                startTimer(A, RxmtInterval);
                timerOn = true;
            }
        }
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt()
    {
        stopTimer(A);
        if (!flyingBufferA.isEmpty()) {
            Packet retransmitPacket = flyingBufferA.get(flyingBufferA.keySet().iterator().next());
            toLayer3(A, retransmitPacket);
            retransmitNum++;
            lostNum++;
            startTimer(A, RxmtInterval);
        }
        else {
            startTimer(A, RxmtInterval);
        }
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        //sendWindowStart = 0;
        nextSeq = FirstSeqNo;
        flyingBufferA = new LinkedHashMap<>();
        sendingBuffer = new LinkedList<>();
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        int seqNum = packet.getSeqnum();
        int checksum = packet.getChecksum();
        String payload = packet.getPayload();
        System.out.println("rcv packet seqnum: " + seqNum + ", expect " + expectedSeqNum);
        if(checkCheckSum(checksum, packet.getPayload())){
            if (expectedSeqNum > seqNum) {
                Packet response = new Packet(seqNum, expectedSeqNum - 1, checksum, payload);
                toLayer3(B,response);
                ACK_B++;
            }
            else if (expectedSeqNum == seqNum) {
                System.out.println("here");
                rcvBufferB.put(seqNum, packet);
                while(rcvBufferB.containsKey(expectedSeqNum)){
                    toLayer5(rcvBufferB.get(expectedSeqNum).getPayload());
                    delivered++;
                    rcvBufferB.remove(expectedSeqNum);
                    expectedSeqNum++;
                }
                Packet response = new Packet(seqNum, expectedSeqNum - 1, checksum, payload);
                toLayer3(B,response);
                ACK_B++;
            }
            else {
                rcvBufferB.put(seqNum, packet);
            }
        }
        else{
            corruptedNum++;
        }
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        expectedSeqNum = FirstSeqNo;
        rcvBufferB = new HashMap<>();
    }

    private int receivedCntA = 0;
    private int origTransmittedCntA = 0;
    private int retransmitNum = 0;
    private int delivered = 0;
    private int ACK_B = 0;
    private int corruptedNum = 0;
    private int lostNum = 0;
    // Use to print final statistics
    protected void Simulation_done()
    {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + origTransmittedCntA);
        System.out.println("Number of retransmissions by A:" + retransmitNum);
        System.out.println("Number of data packets delivered to layer 5 at B:" + delivered);
        System.out.println("Number of ACK packets sent by B:" + ACK_B);
        System.out.println("Number of corrupted packets:" + corruptedNum);
        System.out.println("Ratio of lost packets:" + lostNum);
        System.out.println("Ratio of corrupted packets:" + corruptedNum);
        System.out.println("Average RTT:" + "<YourVariableHere>");
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
        System.out.println("A received packet cnt: "+ receivedCntA);
        System.out.println("Number of packets in sending buffer of A: " + sendingBuffer.size());
        System.out.println("Number of packets in flying buffer of A: " + flyingBufferA.size());
        System.out.println("Number of packets in rcv buffer of B: " + rcvBufferB.size());
    }
}