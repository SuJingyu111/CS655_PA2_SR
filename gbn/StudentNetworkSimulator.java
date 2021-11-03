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

    //region gbn sack
    // statistics
    int origTransmitCntA = 0, reTransmitCntA = 0;
    int packetReceivedB, delieveredCntB = 0, ackSentCntB = 0;
    int corruptedCnt = 0;
    int timeOutCnt = 0;

    // A
    private int aBaseOverall, nextSeqnumOverall, aIncommingSeqnum;
    private ArrayList<Packet> aBuffer;
    // N is window size

    // B
    private int bExpectedSeqnum, bSackLenLimit;
    private HashMap<Integer, Packet> bBuffer;
    private ArrayList<Integer> bSack;
    // N is window size
    //endregion

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

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
        LimitSeqNo = winsize*2; // Part 2 GBN
        RxmtInterval = 3*delay;
    }

    //region helper function
    public void addChecksum(Packet p) {
        int newChecksum = caculateChecksum(p);
        p.setChecksum(newChecksum);
    }

    public int caculateChecksum(Packet p) {
        int newChecksum = 0;
        newChecksum += p.getSeqnum();
        newChecksum += p.getAcknum();
        for (Character c : p.getPayload().toCharArray()) {
            newChecksum += Character.getNumericValue(c);
        }
        for (int i = 0; i < p.getSack().length; i++) {
            newChecksum += p.getSack()[i];
        }
        return newChecksum;
    }

    public boolean evaluateChecksum(Packet p) {
        int newChecksum = 0;
        newChecksum += p.getSeqnum();
        newChecksum += p.getAcknum();
        for (Character c : p.getPayload().toCharArray()) {
            newChecksum += Character.getNumericValue(c);
        }
        for (int i = 0; i < p.getSack().length; i++) {
            newChecksum += p.getSack()[i];
        }
        return newChecksum == p.getChecksum();
    }

    public int convertExpectedSeqnumToCurSeqnum(int expectedSeqnum) {
        return subtructFromSeqnum(expectedSeqnum);
    }

    public int[] getSACK(ArrayList<Integer> sackList) {
        if (sackList.size() > this.bSackLenLimit) {
            System.out.println("In getSACK, sackList has length longer than bSackLenLimit!");
            System.exit(1);
        }
        int[] resultSack = new int[this.bSackLenLimit];
        for (int i = 0; i < sackList.size(); i++) {
            resultSack[i] = sackList.get(i);
        }
        return resultSack;
    }

    public int subtructFromSeqnum(int seqnum) {
        if (seqnum <= 0 || seqnum > this.LimitSeqNo) {
            System.out.println("In subtructFromSeqnum, wrong seqnum: " + seqnum + "!");
            System.exit(1);
        }
        if (seqnum == 1) {
            return this.LimitSeqNo;
        }
        return seqnum - 1;
    }

    public int addOneToSeqnum(int seqnum) {
        if (seqnum <= 0 || seqnum > this.LimitSeqNo) {
            System.out.println("In addOneToSeqnum, wrong seqnum: " + seqnum + "!");
            System.exit(1);
        }
        if (seqnum == LimitSeqNo) {
            return 1;
        }
        return seqnum + 1;
    }


    private boolean inWindow(int bExpectedSeqnum, int windowSize, int i) {
        if (i < bExpectedSeqnum) {
            return i + LimitSeqNo < bExpectedSeqnum + windowSize;
        }
        return i < bExpectedSeqnum + windowSize;
    }

    public Packet createACKPacket() {
        Packet curPacket = new Packet(0,
                this.convertExpectedSeqnumToCurSeqnum(this.bExpectedSeqnum),
                0,
                this.getSACK(this.bSack));
        addChecksum(curPacket);
        return curPacket;
    }

    public Packet createPacketWithMessage(Message message) {
        Packet curPacket = new Packet(aIncommingSeqnum,
                0,
                0,
                message.getData(),
                new int[5]);
        addChecksum(curPacket);
        aIncommingSeqnum = addOneToSeqnum(aIncommingSeqnum);
        return curPacket;
    }

    private void addToBSack(int packetSeqnum) {
        bSack.add(packetSeqnum);
        if (bSack.size() > bSackLenLimit) {
            bSack.remove(0);
        }
    }

    public int overallToLocalSeqnum(int overallSeqnum) {
        int localSeqnum = overallSeqnum % LimitSeqNo;
        return localSeqnum + 1;
    }

    public void aSendPacket(Packet packet) {
        System.out.println("A, send: " + packet.getSeqnum());
        toLayer3(A, packet);
        stopTimer(A);
        startTimer(A, RxmtInterval);
    }

    private int getLeap(int abase, int acknum) {
        if (acknum < abase) {
            return (LimitSeqNo - abase) + (acknum - 0);
        }
        return acknum - abase;
    }

    public static boolean arrayContains(final int[] arr, final int key) {
        return Arrays.stream(arr).anyMatch(i -> i == key);
    }

    public void bSendAck() {
        Packet curPacket = createACKPacket();
        System.out.println("B, send ACK: " + curPacket.getAcknum());
        toLayer3(B, curPacket);
    }
    //endregion
    
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {
        System.out.println("------- A, aOutput -------");
        System.out.println("Before: ");
        System.out.println("nextSeqnumOverall: " + nextSeqnumOverall);
        System.out.println("aBaseOverall: " + aBaseOverall);
        System.out.println("aIncommingSeqnum: " + aIncommingSeqnum);

        Packet curPacket = createPacketWithMessage(message);
        aBuffer.add(curPacket);
        if (nextSeqnumOverall < aBaseOverall + WindowSize) {
            aSendPacket(aBuffer.get(nextSeqnumOverall));
            origTransmitCntA++;
            nextSeqnumOverall++;
        }

        System.out.println("After: ");
        System.out.println("nextSeqnumOverall: " + nextSeqnumOverall);
        System.out.println("aBaseOverall: " + aBaseOverall);
        System.out.println("aIncommingSeqnum: " + aIncommingSeqnum);
        System.out.println("--------------------------");


    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        int acknum = packet.getAcknum();
        int aBaseLocal = overallToLocalSeqnum(aBaseOverall);
        // if corrupted
        if (!evaluateChecksum(packet)) {
            corruptedCnt++;
            System.out.println("A, corrupt");
            for (int i = aBaseOverall; i < nextSeqnumOverall; i++) {
                aSendPacket(aBuffer.get(i));
            }
        }
        // no corrupt
        // if acknum in packet, do not trigger retransmission
        else if (inWindow(aBaseLocal, WindowSize, acknum)) {
            System.out.println("A, in window: " + acknum);
            System.out.println("aBaseLocal: " + aBaseLocal);
            System.out.println("aBaseOverall: " + aBaseOverall + "nextSeqnumOverall: " + nextSeqnumOverall);
            int leapForward = getLeap(aBaseLocal, acknum) + 1;
            aBaseOverall += leapForward;
            System.out.println("aBaseOverall: " + aBaseOverall + "nextSeqnumOverall: " + nextSeqnumOverall);
//            if (aBaseOverall > nextSeqnumOverall) {
//                System.out.println("In aInput, aBaseLocal > nextSeqnumOverall");
//                System.exit(1);
//            }
            // reach the end of the buffer
            if (aBaseOverall >= nextSeqnumOverall) {
                System.out.println("A, reach the end of buffer, stop timer");
                stopTimer(A);
            } else {
                while (nextSeqnumOverall < aBaseOverall + WindowSize && aBuffer.size() > nextSeqnumOverall) {
                    aSendPacket(aBuffer.get(nextSeqnumOverall));
                    nextSeqnumOverall++;
                }
            }
        }
        // ack num not in window, retransmit
        else {
            reTransmitCntA++;
            System.out.println("A, retransmit, not in window: " + acknum);
            for (int i = aBaseOverall; i < nextSeqnumOverall; i++) {
                int localI = overallToLocalSeqnum(i);
                if (!arrayContains(packet.getSack(), localI)) {
                    aSendPacket(aBuffer.get(i));
                }
            }
        }
    }

    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {
        reTransmitCntA++;
        System.out.println("A, timeout");
        for (int i = aBaseOverall; i < nextSeqnumOverall; i++) {
            aSendPacket(aBuffer.get(i));
        }
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        this.aBaseOverall = 0;
        this.nextSeqnumOverall = 0;
        this.aIncommingSeqnum = 1;
        this.aBuffer = new ArrayList<Packet>();
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        // corrupted
        if (!evaluateChecksum(packet)) {
            corruptedCnt++;
            System.out.println("B, corrupt packet");
            bSendAck();
        }
        // not corrupted
        // in order
        else if (packet.getSeqnum() == bExpectedSeqnum) {
            System.out.println("B, in order: " + packet.getSeqnum());
            bExpectedSeqnum = addOneToSeqnum(bExpectedSeqnum);
            toLayer5(packet.getPayload());
            delieveredCntB++;

            int i = bExpectedSeqnum;
            while (inWindow(bExpectedSeqnum, WindowSize, i) && bBuffer.containsKey(i)) {
                // update buffer and sack, to layer5 if possible
                toLayer5(bBuffer.get(i).getPayload());
                delieveredCntB++;
                bBuffer.remove(i);
                int finalI = i;
                bSack.removeIf(a -> (a == finalI));

                i = addOneToSeqnum(i);
            }
            bExpectedSeqnum = i;

            bSendAck();
        }
        // not in order
        else {
            System.out.println("B, not in order: " + packet.getSeqnum());
            // if in window size, add to buffer and SACK
            int packetSeqnum = packet.getSeqnum();
            if (inWindow(bExpectedSeqnum, WindowSize, packetSeqnum)) {
                bBuffer.put(packetSeqnum, packet);
                addToBSack(packetSeqnum);
            }

            bSendAck();
        }

    }

    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        bExpectedSeqnum = 1;
        bBuffer = new HashMap<Integer, Packet>();
        bSack = new ArrayList<Integer>();
        bSackLenLimit = 5;
    }

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
