package com.androiddrop.host;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Works out the URLs that other devices on the local network can use to reach this server. */
@Component
public class ServerAddresses {

	/** macOS/Linux interfaces that never carry LAN traffic a phone could use (AirDrop, VMs, containers...). */
	private static final Set<String> IGNORED_PREFIXES = Set.of("awdl", "llw", "utun", "bridge", "vmnet", "docker",
			"veth", "vboxnet", "anpi", "ap");

	private final Environment environment;

	public ServerAddresses(Environment environment) {
		this.environment = environment;
	}

	public int port() {
		Integer configured = environment.getProperty("server.port", Integer.class, 8080);
		return environment.getProperty("local.server.port", Integer.class, configured);
	}

	/** Base URLs such as {@code http://192.168.1.20:8080}, most likely first (Wi-Fi/Ethernet on en0). */
	public List<String> lanUrls() {
		return lanAddresses().stream().map(address -> "http://" + address + ":" + port()).toList();
	}

	private volatile Set<String> ownAddresses = Set.of();
	private volatile long ownAddressesCheckedAt;

	/** Whether {@code address} belongs to this computer (checked against its interfaces at most every 30 seconds). */
	public boolean isOwnAddress(String address) {
		long now = System.nanoTime();
		if (now - ownAddressesCheckedAt > TimeUnit.SECONDS.toNanos(30)) {
			ownAddresses = Set.copyOf(lanAddresses());
			ownAddressesCheckedAt = now;
		}
		return ownAddresses.contains(address);
	}

	/** This computer's IPv4 addresses on the local network, most likely first. */
	public List<String> lanAddresses() {
		record Candidate(String interfaceName, int index, String address) {
		}
		List<Candidate> candidates = new ArrayList<>();
		try {
			for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || nic.isPointToPoint() || isIgnored(nic.getName())) {
					continue;
				}
				for (InetAddress address : Collections.list(nic.getInetAddresses())) {
					if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
						candidates.add(new Candidate(nic.getName(), nic.getIndex(), address.getHostAddress()));
					}
				}
			}
		}
		catch (SocketException ex) {
			return List.of();
		}
		return candidates.stream()
			.sorted(Comparator.comparing((Candidate c) -> !c.interfaceName().equals("en0")).thenComparing(Candidate::index))
			.map(Candidate::address)
			.distinct()
			.toList();
	}

	private static boolean isIgnored(String interfaceName) {
		return IGNORED_PREFIXES.stream().anyMatch(interfaceName::startsWith);
	}

}
