/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.usgs.anss.query.cwb.data;

import gov.usgs.anss.query.cwb.formatter.CWBQueryFormatter;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.query.NSCL;
import gov.usgs.anss.query.cwb.messages.MessageFormatter;
import gov.usgs.anss.seed.MiniSeed;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import nz.org.geonet.HashCodeUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 *
 * @author geoffc
 */
public class CWBDataServerMSEED implements CWBDataServer {

    private static final Logger logger = Logger.getLogger(CWBDataServerMSEED.class.getName());
    private static DateTimeFormatter hmsFormat = ISODateTimeFormat.time().withZone(DateTimeZone.forID("UTC"));


    static {
        logger.fine("$Id: CWBServerImpl.java 1806 2010-02-03 02:59:12Z geoffc $");
    }
    private String host;
    private int port;
    private Socket ds = null;
    private InputStream inStream;
    private OutputStream outStream;
    private LinkedBlockingQueue<MiniSeed> incomingMiniSEED;
    private NSCL newNSCL = null;
    private NSCL lastNSCL = null;
    private boolean quiet = false;

    /**
     * Provides methods for running queries against a CWB server.
     *
     * Typical usage would look like:
     *
     * cwbServer = new CWBDataServerMSEED("cwb.geonet.org.nz", 80);
     *   cwbServer.query(begin, duration, nscl);
     *
     *  while (cwbServer.hasNext()) {
     *       result.add(cwbServer.getNext());
     *   }
     *
     *
     * @param host the CWB server name.
     * @param port the CWB server port.
     */
    public CWBDataServerMSEED(String host, int port) {
        this.host = host;
        this.port = port;

    }

	/**
	 * @return the host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * @param host the host to set
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * @param port the port to set
	 */
	public void setPort(int port) {
		this.port = port;
	}

    /**
     * Runs a query against the server.
     *
     * @param begin the start time for the data query.
     * @param duration the duration in seconds to extract data for.
     * @param nscl the network, station, channel, and location data to query for.  These are all possible wild carded.
     */
    public void query(DateTime begin, Double duration, String nsclSelectString) {

        while (ds == null) {

            try {
                ds = new Socket(this.getHost(), this.getPort());
            } catch (UnknownHostException ex) {
                ds = null;
                logger.warning("Cannot resolve the host: " + this.getHost());
            } catch (IOException ex) {
                ds = null;
                if (ex.getMessage() != null) {
                    if (ex.getMessage().indexOf("Connection refused") >= 0) {
                        logger.warning("Problem connecting to " + this.getHost() + ":" + this.getPort() + "  Either the server is down or there is an internet connection problem. " + "Will try again in 20 seconds.");
            }
                } else {
                    logger.warning("Got IOError opening socket to server e=" + ex);
                }
            }

            if (ds == null) {
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException ex) {
                    // Presumably only if we get sigtermed etc.
                }
            }
        }

        try {
            inStream = ds.getInputStream();
            outStream = ds.getOutputStream();
            outStream.write(CWBQueryFormatter.miniSEED(begin, duration, nsclSelectString).getBytes());
        } catch (IOException ex) {
            Logger.getLogger(CWBDataServerMSEED.class.getName()).log(Level.SEVERE, null, ex);
        }

        incomingMiniSEED = new LinkedBlockingQueue<MiniSeed>();
    }

    /**
     * Returns the next data record.  This is equivalent to the data for a fully qualified NSCL.
     *
     * @return
     */
    public TreeSet<MiniSeed> getNext() {

        TreeSet<MiniSeed> blks = new TreeSet<MiniSeed>();

        byte[] b = new byte[4096];
        try {
            read:
            while (read(inStream, b, 0, 512)) {
                MiniSeed ms = null;
                if (b[0] == '<' && b[1] == 'E' && b[2] == 'O' && b[3] == 'R' && b[4] == '>') {
                    logger.fine("EOR found");
                    break read;
                } else {

                    try {
                        ms = new MiniSeed(b);
                    } catch (IllegalSeednameException ex) {
                        Logger.getLogger(CWBDataServerMSEED.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    if (ms.getBlockSize() != 512) {
                        read(inStream, b, 512, ms.getBlockSize() - 512);

                        try {
                            ms = new MiniSeed(b);
                        } catch (IllegalSeednameException ex) {
                            Logger.getLogger(CWBDataServerMSEED.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                }

                if (ms != null) {

                    if (ms.getIndicator().compareTo("D ") < 0) {
                        continue read;
                    }

                    // This sets up the NSCL on the very first miniSEED block
                    if (lastNSCL == null) {
                        lastNSCL = NSCL.stringToNSCL(ms.getSeedName());
                    }

                    newNSCL = NSCL.stringToNSCL(ms.getSeedName());

                    if (newNSCL.equals(lastNSCL)) {
                        incomingMiniSEED.add(ms);
                        lastNSCL = newNSCL;
                    } else {
                        incomingMiniSEED.drainTo(blks);
                        incomingMiniSEED.add(ms);
                        lastNSCL = newNSCL;
                        break read;
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(CWBDataServerMSEED.class.getName()).log(Level.SEVERE, null, ex);
        }

        // This is triggered for the last channel off the stream.
        if (blks.isEmpty()) {
            incomingMiniSEED.drainTo(blks);
        }

        if (!quiet) {
            logger.info(MessageFormatter.miniSeedSummary(new DateTime(), blks));
        }

        return blks;
    }

    /**
     * Returns true if there are more data records.
     *
     * @return
     */
    public boolean hasNext() {
        if (lastNSCL == null) {
			return true;
		}
		return !incomingMiniSEED.isEmpty();
	}

    public static boolean read(InputStream in, byte[] b, int off, int l)
            throws IOException {
        int len;
        while ((len = in.read(b, off, l)) > 0) {
            off += len;
            l -=
                    len;
            if (l == 0) {
                return true;
            }

        }
        return false;
    }

    /**
     * By default some progress information is provided to the user.
     * This turns that output off.
     **/
    public void quiet() {
        this.quiet = true;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        try {
            outStream.write(("\n").getBytes());
        } catch (IOException ex) {
            Logger.getLogger(CWBDataServerMSEED.class.getName()).log(Level.FINE,
                    "Failed when attempting to close connection.", ex);
        }
        ds.close();
    }

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (obj.getClass() == this.getClass()) {
			final CWBDataServerMSEED other = (CWBDataServerMSEED) obj;
			if (getHost().equals(other.getHost()) &&
					getPort() == other.getPort()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		int result = HashCodeUtil.SEED;
		result = HashCodeUtil.hash(result, getHost());
		result = HashCodeUtil.hash(result, getPort());
		return result;
	}
}
