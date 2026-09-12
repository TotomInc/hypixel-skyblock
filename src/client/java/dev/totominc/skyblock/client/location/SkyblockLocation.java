package dev.totominc.skyblock.client.location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/**
 * Lightweight Hypixel SkyBlock location detector based on the server address
 * and sidebar scoreboard. The End is identified from the area line, matching
 * the scoreboard-driven approach used by Skyblocker.
 */
public final class SkyblockLocation {
	private static final String AREA_ICON = "\uE067";
	private static final String RIFT_AREA_ICON = "\uE020";

	private static boolean onHypixel;
	private static boolean onSkyblock;
	private static boolean inTheEnd;
	private static String area = "";

	private SkyblockLocation() {
	}

	public static boolean isOnHypixel() {
		return onHypixel;
	}

	public static boolean isOnSkyblock() {
		return onSkyblock;
	}

	public static boolean isInTheEnd() {
		return inTheEnd;
	}

	public static String area() {
		return area;
	}

	public static void reset() {
		onHypixel = false;
		onSkyblock = false;
		inTheEnd = false;
		area = "";
	}

	public static void update(Minecraft client) {
		onHypixel = isConnectedToHypixel(client);

		List<String> sidebar = readSidebar(client);
		String title = sidebar.isEmpty() ? "" : sidebar.getFirst();
		onSkyblock = onHypixel && containsIgnoreCase(title, "SKYBLOCK");

		if (!onSkyblock) {
			inTheEnd = false;
			area = "";
			return;
		}

		area = parseArea(sidebar);
		inTheEnd = "The End".equalsIgnoreCase(area);
	}

	private static boolean isConnectedToHypixel(Minecraft client) {
		ServerData server = client.getCurrentServer();
		String address = server != null && server.ip != null ? server.ip.toLowerCase(Locale.ENGLISH) : "";
		String brand = client.player != null && client.player.connection != null && client.player.connection.serverBrand() != null
			? client.player.connection.serverBrand()
			: "";

		return address.contains("hypixel.net") || address.contains("hypixel.io") || brand.contains("Hypixel BungeeCord");
	}

	private static List<String> readSidebar(Minecraft client) {
		List<String> lines = new ArrayList<>();
		ClientLevel level = client.level;

		if (level == null) {
			return lines;
		}

		Scoreboard scoreboard = level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

		if (objective == null) {
			return lines;
		}

		for (ScoreHolder scoreHolder : scoreboard.getTrackedPlayers()) {
			if (!scoreboard.listPlayerScores(scoreHolder).containsKey(objective)) {
				continue;
			}

			PlayerTeam team = scoreboard.getPlayersTeam(scoreHolder.getScoreboardName());

			if (team == null) {
				continue;
			}

			String line = ChatFormatting.stripFormatting(team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());

			if (line != null && !line.isBlank()) {
				lines.add(line.strip());
			}
		}

		lines.add(strip(objective.getDisplayName()));
		return lines.reversed();
	}

	private static String parseArea(List<String> sidebar) {
		for (String line : sidebar) {
			if (line.indexOf(AREA_ICON) < 0 && line.indexOf(RIFT_AREA_ICON) < 0 && !line.contains("The End") && !containsIgnoreCase(line, "Area:")) {
				continue;
			}

			String cleaned = line.replace(AREA_ICON, "").replace(RIFT_AREA_ICON, "").replace("⏣", "");
			int areaIndex = indexOfIgnoreCase(cleaned, "Area:");

			if (areaIndex >= 0) {
				cleaned = cleaned.substring(areaIndex + "Area:".length());
			}

			cleaned = cleaned.strip();

			if (!cleaned.isEmpty()) {
				return cleaned;
			}
		}

		return "";
	}

	private static String strip(Component component) {
		return ChatFormatting.stripFormatting(component.getString()).strip();
	}

	private static boolean containsIgnoreCase(String value, String needle) {
		return indexOfIgnoreCase(value, needle) >= 0;
	}

	private static int indexOfIgnoreCase(String value, String needle) {
		return value.toLowerCase(Locale.ENGLISH).indexOf(needle.toLowerCase(Locale.ENGLISH));
	}
}
