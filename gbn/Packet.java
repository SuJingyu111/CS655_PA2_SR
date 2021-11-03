public class Packet
{
    private int seqnum;
    private int acknum;
    private int checksum;
    private String payload;

    //region gbn sack
    private int[] sack;
    //endregion

    public Packet(Packet p)
    {
        seqnum = p.getSeqnum();
        acknum = p.getAcknum();
        checksum = p.getChecksum();
        payload = new String(p.getPayload());

        //region gbn sack
        this.sack = p.getSack();
        //endregion

    }
    
    public Packet(int seq, int ack, int check, String newPayload, int[] sack)
    {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        if (newPayload == null)
        {
            payload = "";
        }        
        else if (newPayload.length() > NetworkSimulator.MAXDATASIZE)
        {
            payload = null;
        }
        else
        {
            payload = new String(newPayload);
        }

        //region gbn sack
        this.sack = sack;
        //endregion

    }
    
    public Packet(int seq, int ack, int check, int[] sack)
    {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        payload = "";

        //region gbn sack
        this.sack = sack;
        //endregion

    }

    //region gbn sack
    public int[] getSack() {
        return sack;
    }

    public void setSack(int[] sack) {
        this.sack = sack;
    }
    //endregion

    public boolean setSeqnum(int n)
    {
        seqnum = n;
        return true;
    }
    
    public boolean setAcknum(int n)
    {
        acknum = n;
        return true;
    }
    
    public boolean setChecksum(int n)
    {
        checksum = n;
        return true;
    }
    
    public boolean setPayload(String newPayload)
    {
        if (newPayload == null)
        {
            payload = "";
            return false;
        }        
        else if (newPayload.length() > NetworkSimulator.MAXDATASIZE)
        {
            payload = "";
            return false;
        }
        else
        {
            payload = new String(newPayload);
            return true;
        }
    }
    
    public int getSeqnum()
    {
        return seqnum;
    }
    
    public int getAcknum()
    {
        return acknum;
    }
    
    public int getChecksum()
    {
        return checksum;
    }
    
    public String getPayload()
    {
        return payload;
    }
    
    public String toString()
    {
        return("seqnum: " + seqnum + "  acknum: " + acknum + "  checksum: " +
               checksum + "  payload: " + payload);
    }
    
}
