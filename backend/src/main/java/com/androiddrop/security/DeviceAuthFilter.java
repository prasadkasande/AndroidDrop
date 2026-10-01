package com.androiddrop.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.androiddrop.device.Device;
import com.androiddrop.device.DeviceService;
import com.androiddrop.host.ServerAddresses;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Decides which {@link Device} is calling {@code /api/**} and stores it in the {@link #DEVICE} request attribute.
 * <ul>
 * <li>Requests from this computer are the host: over loopback addressed to {@code localhost}, or from one of its
 * own network addresses addressed to that same address (someone on the Mac opening the Wi-Fi URL). Checking the
 * Host header as well stops DNS-rebinding pages from borrowing that trust.</li>
 * <li>Everyone else must present a paired device token cookie.</li>
 * <li>State-changing requests must carry {@link #CSRF_HEADER}. Browsers only let other origins send custom headers
 * after a CORS preflight, which this server never approves, so this blocks cross-site form posts.</li>
 * </ul>
 */
@Component
public class DeviceAuthFilter extends OncePerRequestFilter {

	public static final String DEVICE = "androiddrop.device";
	/** The token cookie the caller signed in with, if it isn't the host: one per browser. */
	public static final String TOKEN = "androiddrop.token";
	public static final String TOKEN_COOKIE = "adrop_token";
	public static final String CSRF_HEADER = "X-AndroidDrop";

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
	private static final Set<String> LOCAL_HOST_NAMES = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
	private static final Set<String> PUBLIC_POSTS = Set.of("/api/pair", "/api/pair/devices", "/api/pair/handoff");

	private final DeviceService devices;
	private final ServerAddresses addresses;

	public DeviceAuthFilter(DeviceService devices, ServerAddresses addresses) {
		this.devices = devices;
		this.addresses = addresses;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
			reject(response, HttpStatus.FORBIDDEN, "Cross-site requests are not allowed.");
			return;
		}
		if (!SAFE_METHODS.contains(request.getMethod()) && request.getHeader(CSRF_HEADER) == null) {
			reject(response, HttpStatus.FORBIDDEN, "Missing " + CSRF_HEADER + " header.");
			return;
		}
		String token = tokenCookie(request);
		Optional<Device> device = isFromThisComputer(request) ? Optional.of(devices.host()) : devices.findByToken(token);
		if (device.isPresent()) {
			request.setAttribute(DEVICE, device.get());
			if (!device.get().host()) {
				request.setAttribute(TOKEN, token);
			}
		}
		else if (!isPublic(request)) {
			reject(response, HttpStatus.UNAUTHORIZED, "This device isn't paired yet.");
			return;
		}
		chain.doFilter(request, response);
	}

	private boolean isFromThisComputer(HttpServletRequest request) {
		String client = clientAddress(request);
		String host = request.getServerName().toLowerCase(Locale.ROOT);
		if (isLoopback(client)) {
			return LOCAL_HOST_NAMES.contains(host);
		}
		return client.equals(host) && addresses.isOwnAddress(client);
	}

	/**
	 * The peer address, looking through a proxy on this same machine (the Vite dev server) via the entry it appended
	 * to X-Forwarded-For. Only a local process can make us read that header, so it can't be used to gain trust.
	 */
	static String clientAddress(HttpServletRequest request) {
		String remote = request.getRemoteAddr();
		String forwarded = request.getHeader("X-Forwarded-For");
		if (isLoopback(remote) && forwarded != null && !forwarded.isBlank()) {
			String[] hops = forwarded.split(",");
			return hops[hops.length - 1].strip();
		}
		return remote;
	}

	static boolean isLoopback(String address) {
		return address != null && (address.startsWith("127.") || address.equals("::1")
				|| address.equals("0:0:0:0:0:0:0:1") || address.startsWith("::ffff:127."));
	}

	private static boolean isPublic(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return (PUBLIC_POSTS.contains(uri) && request.getMethod().equals("POST"))
				|| (uri.equals("/api/server") && request.getMethod().equals("GET"));
	}

	private static String tokenCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		return Arrays.stream(cookies)
			.filter(cookie -> TOKEN_COOKIE.equals(cookie.getName()))
			.map(Cookie::getValue)
			.findFirst()
			.orElse(null);
	}

	private static void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType("application/problem+json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter()
			.write("{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\"}".formatted(status.value(),
					status.getReasonPhrase(), detail));
	}

}
