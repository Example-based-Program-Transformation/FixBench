package org.jgroups.util;

import org.jgroups.Address;
import org.jgroups.Global;
import org.jgroups.blocks.LazyRemovalCache;

import java.io.*;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;

/** Logical address which is unique over space and time.
 * <br/>
 * Copied from java.util.UUID, but unneeded fields from the latter have been removed. UUIDs needs to
 * have a small memory footprint.
 * @author Bela Ban
 */
public class UUID implements Address, Streamable, Comparable<Address> {
    protected long   mostSigBits;
    protected long   leastSigBits;

    /** The random number generator used by this class to create random based UUIDs */
    protected static volatile SecureRandom numberGenerator=null;

    /** Keeps track of associations between logical addresses (UUIDs) and logical names */
    protected static LazyRemovalCache<Address,String> cache;

    private static final long serialVersionUID=3972962439975931228L;

    protected static boolean print_uuids=false;

    protected static final int SIZE=Global.LONG_SIZE * 2;

    protected static final LazyRemovalCache.Printable<Address,String> print_function=new LazyRemovalCache.Printable<Address,String>() {
        public java.lang.String print(Address key, String val) {
            return val + ": " + (key instanceof UUID? ((UUID)key).toStringLong() : key) + "\n";
        }
    };
    

    static {
        String tmp;

        int max_elements=500;
        long max_age=5000L;

        try {
            tmp=Util.getProperty(new String[]{Global.UUID_CACHE_MAX_ELEMENTS}, null, null, false, "500");
            if(tmp != null)
                max_elements=Integer.valueOf(tmp);
        }
        catch(Throwable t) {
        }

        try {
            tmp=Util.getProperty(new String[]{Global.UUID_CACHE_MAX_AGE}, null, null, false, "5000");
            if(tmp != null)
                max_age=Long.valueOf(tmp);
        }
        catch(Throwable t) {
        }

        cache=new LazyRemovalCache<Address,String>(max_elements, max_age);


        /* Trying to get value of jgroups.print_uuids. PropertyPermission not granted if
        * running in an untrusted environment with JNLP */
        try {
            tmp=Util.getProperty(new String[]{Global.PRINT_UUIDS}, null, null, false, "false");
            print_uuids=Boolean.valueOf(tmp).booleanValue();
        }
        catch (SecurityException ex){
        }
    }


    public UUID() {
    }


    public UUID(long mostSigBits, long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    /** Private constructor which uses a byte array to construct the new UUID */
    protected UUID(byte[] data) {
        long msb = 0;
        long lsb = 0;
        if(data.length != 16)
            throw new RuntimeException("UUID needs a 16-byte array");
        for (int i=0; i<8; i++)
            msb = (msb << 8) | (data[i] & 0xff);
        for (int i=8; i<16; i++)
            lsb = (lsb << 8) | (data[i] & 0xff);
        this.mostSigBits = msb;
        this.leastSigBits = lsb;
    }


    public static void add(Address uuid, String logical_name) {
        cache.add(uuid, logical_name); // overwrite existing entry
    }

    public static void add(Map<Address,String> map) {
        if(map == null) return;
        for(Map.Entry<Address,String> entry: map.entrySet())
            add(entry.getKey(), entry.getValue());
    }

    public static String get(Address logical_addr) {
        return cache.get(logical_addr);
    }

    /** Returns a <em>copy</em> of the cache's contents */
    public static Map<Address,String> getContents() {
        return cache.contents();
    }

    public static void remove(Address addr) {
        cache.remove(addr);
    }

    public static void removeAll(Collection<Address> mbrs) {
        cache.removeAll(mbrs);
    }

    public static void retainAll(Collection<Address> logical_addrs) {
        cache.retainAll(logical_addrs);
    }

    public static String printCache() {
        return cache.printCache(print_function);
    }


    /**
     * Static factory to retrieve a type 4 (pseudo randomly generated) UUID.
     * The {@code UUID} is generated using a cryptographically strong pseudo
     * random number generator.
     * @return  A randomly generated {@code UUID}
     */
    public static UUID randomUUID() {
        SecureRandom ng=numberGenerator;
        if(ng == null)
            numberGenerator=ng=new SecureRandom();

        byte[] randomBytes=new byte[16];
        ng.nextBytes(randomBytes);
        return new UUID(randomBytes);
    }


    public long getLeastSignificantBits() {
        return leastSigBits;
    }

    /**
     * Returns the most significant 64 bits of this UUID's 128 bit value.
     * @return  The most significant 64 bits of this UUID's 128 bit value
     */
    public long getMostSignificantBits() {
        return mostSigBits;
    }




    public String toString() {
        if(print_uuids)
            return toStringLong();
        String val=cache.get(this);
        return val != null? val : toStringLong();
    }

     /**
     * Returns a {@code String} object representing this {@code UUID}.
     *
     * <p> The UUID string representation is as described by this BNF:
     * <blockquote><pre>
     * {@code
     * UUID                   = <time_low> "-" <time_mid> "-"
     *                          <time_high_and_version> "-"
     *                          <variant_and_sequence> "-"
     *                          <node>
     * time_low               = 4*<hexOctet>
     * time_mid               = 2*<hexOctet>
     * time_high_and_version  = 2*<hexOctet>
     * variant_and_sequence   = 2*<hexOctet>
     * node                   = 6*<hexOctet>
     * hexOctet               = <hexDigit><hexDigit>
     * hexDigit               =
     *       "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
     *       | "a" | "b" | "c" | "d" | "e" | "f"
     *       | "A" | "B" | "C" | "D" | "E" | "F"
     * }</pre></blockquote>
     *
     * @return  A string representation of this {@code UUID}
     */
    public String toStringLong() {
        return (digits(mostSigBits >> 32, 8) + "-" +
                digits(mostSigBits >> 16, 4) + "-" +
                digits(mostSigBits, 4) + "-" +
                digits(leastSigBits >> 48, 4) + "-" +
                digits(leastSigBits, 12));
    }

    /** Returns val represented by the specified number of hex digits. */
    protected static String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }

    /**
     * Returns a hash code for this {@code UUID}.
     * @return  A hash code value for this {@code UUID}
     */
    public int hashCode() {
        return (int)((mostSigBits >> 32) ^
                mostSigBits ^
                (leastSigBits >> 32) ^
                leastSigBits);
    }

    /**
     * Compares this object to the specified object.  The result is {@code
     * true} if and only if the argument is not {@code null}, is a {@code UUID}
     * object, has the same variant, and contains the same value, bit for bit,
     * as this {@code UUID}.
     * @param  obj The object to be compared
     * @return  {@code true} if the objects are the same; {@code false} otherwise
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof UUID))
            return false;
        UUID id = (UUID)obj;
        return this == id || (mostSigBits == id.mostSigBits && leastSigBits == id.leastSigBits);
    }


    /**
     * Compares this UUID with the specified UUID.
     * <p> The first of two UUIDs is greater than the second if the most
     * significant field in which the UUIDs differ is greater for the first UUID.
     * @param  other {@code UUID} to which this {@code UUID} is to be compared
     * @return  -1, 0 or 1 as this {@code UUID} is less than, equal to, or greater than {@code val}
     */
    public int compareTo(Address other) {
        UUID val=(UUID)other;
        if(this == val)
            return 0;
        return (this.mostSigBits < val.mostSigBits ? -1 :
                (this.mostSigBits > val.mostSigBits ? 1 :
                        (this.leastSigBits < val.leastSigBits ? -1 :
                                (this.leastSigBits > val.leastSigBits ? 1 :
                                        0))));
    }



    public void writeTo(DataOutput out) throws IOException {
        out.writeLong(leastSigBits);
        out.writeLong(mostSigBits);
    }

    public void readFrom(DataInput in) throws IOException, IllegalAccessException, InstantiationException {
        leastSigBits=in.readLong();
        mostSigBits=in.readLong();
    }


    public int size() {
        return SIZE;
    }

    public Object clone() throws CloneNotSupportedException {
        return new UUID(mostSigBits, leastSigBits);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(leastSigBits);
        out.writeLong(mostSigBits);
    }


    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        leastSigBits=in.readLong();
        mostSigBits=in.readLong();
    }


}
