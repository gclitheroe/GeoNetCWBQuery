package gov.usgs.anss.query;

import java.util.Comparator;

/**
 * An object for the encapsulation of data and methods for data channels named
 * according to SEED conventions.
 * 
 * All NSCL fields are fixed width, ASCII, alphanumeric fields left justified
 * (no leading spaces), and paded with spaces (after the field’s contents).
 * Network Operator Code, 2 ULN (upper case, lower case or numeric digits).
 * Station Identifier, 5 UN.
 * Channel Identifier, 2 UN.
 * Location Identifier, 2 UN.
 * 
 * @author richardg
 */
public class SeedName {
	private String network,station,channel,location;

	/**
	 * TODO: handle whitespace and/or wildcards...?
	 * @param network
	 * @param station
	 * @param channel
	 * @param location
	 */
	public SeedName(String network, String station, String channel, String location) {
		this.network = network;
		this.station = station;
		this.channel = channel;
		this.location = location;
	}

	/**
	 * Returns a new SeedName object constructed from the NNSSSSSCCCLL input String.
	 * @param input 12 character String formatted as NNSSSSSCCLL.
	 * @return a new SeedName representation of the input NSCL.
	 */
	public static SeedName nsclStringToSeedName(String input) {
		return new SeedName(input.substring(0, 2), input.substring(2, 7),
				input.substring(7, 10), input.substring(10, 12));
	}

	/**
	 * @return the network
	 */
	public String getNetwork() {
		return network;
	}

	/**
	 * SEED: 2 ULN
	 * @param network the network to set
	 */
	public void setNetwork(String network) {
		this.network = network;
		}

	/**
	 * @return the station
	 */
	public String getStation() {
		return station;
	}

	/**
	 * SEED: 5 UN
	 * @param station the station to set
	 */
	public void setStation(String station) {
		this.station = station;
	}

	/**
	 * @return the channel
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * SEED: 3 UN
	 * @param channel the channel to set
	 */
	public void setChannel(String channel) {
		this.channel = channel;
	}

	/**
	 * @return the location
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * SEED: 2 UN
	 * @param location the location to set
	 */
	public void setLocation(String location) {
		this.location = location;
	}

	/**
	 * True if both SeedName objects are not null and both NSCL String
	 * components are equal.
	 * Throws an NullPointerException if any of the NSCL components in this are null.
	 * @param obj
	 * @return
	 */
	@Override
	public boolean equals(Object obj) {
		SeedName other = (SeedName) obj;
		if (other == null) {
			return false;
		}

		return this.network.equals(other.network) &&
				this.station.equals(other.station) &&
				this.channel.equals(other.channel) &&
				this.location.equals(other.location);
	}

	@Override
	public String toString() {
		return (network + station + channel + location);
	}

	public class NetworkComparator implements Comparator {
		public int compare(Object o1, Object o2) {
			SeedName s1 = (SeedName) o1;
			SeedName s2 = (SeedName) o2;
			return s1.getNetwork().compareTo(s2.getNetwork());
		}
	}

	public class StationComparator implements Comparator {
		private NetworkComparator nc = new NetworkComparator();
		public int compare(Object o1, Object o2) {
			int result = nc.compare(o1, o2);
			if (result != 0) {
				return result;
			}
			SeedName s1 = (SeedName) o1;
			SeedName s2 = (SeedName) o2;
			return s1.getStation().compareTo(s2.getStation());
		}
	}

	public class ChannelComparator implements Comparator {
		private StationComparator nc = new StationComparator();
		public int compare(Object o1, Object o2) {
			int result = nc.compare(o1, o2);
			if (result != 0) {
				return result;
			}
			SeedName s1 = (SeedName) o1;
			SeedName s2 = (SeedName) o2;
			return s1.getChannel().compareTo(s2.getChannel());
		}
	}

	public class LocationComparator implements Comparator {
		private ChannelComparator nc = new ChannelComparator();
		public int compare(Object o1, Object o2) {
			int result = nc.compare(o1, o2);
			if (result != 0) {
				return result;
			}
			SeedName s1 = (SeedName) o1;
			SeedName s2 = (SeedName) o2;
			return s1.getLocation().compareTo(s2.getLocation());
		}
	}
}
