/*
 * Copyright (c) 2018, Cameron <https://github.com/noremac201>
 * Copyright (c) 2018, Jacob M <https://github.com/jacoblairm>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.BaPB;

import com.google.inject.Provides;
import java.awt.Image;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;
import net.runelite.http.api.chat.ChatClient;
import org.apache.commons.text.WordUtils;

@PluginDescriptor(
	name = "Barbarian Assault Personal Bests",
	description = "Emulates the normal plugin as well as saving personal bests",
	tags = {"minigame", "overlay", "timer", "Personal Bests","Time"}
)
@Slf4j
public class BaPBPlugin extends Plugin
{
	private static final int BA_WAVE_NUM_INDEX = 2;
	private static final String START_WAVE = "1";
	private static final String ENDGAME_REWARD_NEEDLE_TEXT = "<br>5";
	private double currentpb; //This is to load overall pb
	private double rolecurrentpb; //This is to load role specific pb's and gets set when the role is determined
	private static final String BA_COMMAND_STRING = "!ba";

	@Getter(AccessLevel.PACKAGE)
	private int inGameBit = 0;
	private String currentWave = START_WAVE;
	private GameTimer gameTime;
	private String round_role;
	private Boolean scanning;
	private int round_roleID;
	private Boolean leech;
	//defines all of my specific widgets and icon names could I do it better yes, but like it works
	private Integer BaRoleWidget = 256;
	private Integer BaScrollWidget = 159;
	private Integer leaderID = 8;
	private Integer player1ID = 9;
	private Integer player2ID = 10;
	private Integer player3ID = 11;
	private Integer player4ID = 12;
	private Integer leadericonID = 18;
	private Integer player1iconID = 19;
	private Integer player2iconID = 20;
	private Integer player3iconID = 21;
	private Integer player4iconID = 22;
	private Integer attackerIcon = 20561;
	private Integer defenderIcon = 20566;
	private Integer collectorIcon = 20563;
	private Integer healerIcon = 20569;

	@Inject
	private Client client;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BaPBConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private ChatClient chatClient;

	@Inject
	private ScheduledExecutorService executor;

	@Provides
	BaPBConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BaPBConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		chatCommandManager.registerCommandAsync(BA_COMMAND_STRING, this::baLookup, this::baSubmit);
		scanning = false;
	}

	@Override
	protected void shutDown() throws Exception
	{
		chatCommandManager.unregisterCommand(BA_COMMAND_STRING);
		scanning = false;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case WidgetID.BA_REWARD_GROUP_ID:
			{
				Widget rewardWidget = client.getWidget(WidgetInfo.BA_REWARD_TEXT);

				if (rewardWidget != null && rewardWidget.getText().contains(ENDGAME_REWARD_NEEDLE_TEXT) && gameTime != null)
				{
					if ((gameTime.getPBTime() < rolecurrentpb || rolecurrentpb == 0.0) && config.Seperate())
					{
						configManager.setRSProfileConfiguration("BaPB", round_role, gameTime.getPBTime());
						log.info("Personal best of: {} saved in {}",gameTime.getPBTime(), round_role);
					}
					currentpb = getCurrentPB("Barbarian Assault");
					if ((gameTime.getPBTime() < currentpb || currentpb == 0.0))
					{
						configManager.setRSProfileConfiguration("BaPB", "Barbarian Assault", gameTime.getPBTime());
						log.info("Personal best of: {} saved in Barbarian Assault",gameTime.getPBTime());
					}
					configManager.setRSProfileConfiguration("BaPB", "Recent", gameTime.getPBTime());
					gameTime = null;
				}

				break;
			}
			case WidgetID.BA_TEAM_GROUP_ID: {
				scanning = true;
				leech = false;
			}
			case 159: {//this is to set scannign true when scroll is used on someone
				scanning = true;
			}
			case 158: {//this is to set scannign true when scroll is used on someone
				scanning = true;
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event){
		if (event.getGroupId() == BaRoleWidget) scanning = false;//sets scanning to false when leaving w1 or leaving for any reason
	}


	@Subscribe
	public void onGameTick(GameTick event)
	{
		if(scanning) {
			final String player;
			player = client.getLocalPlayer().getName();
			Widget leader = client.getWidget(BaRoleWidget,leaderID);
			Widget leaderIcon = client.getWidget(BaRoleWidget, leadericonID);
			Widget player1 = client.getWidget(BaRoleWidget,player1ID);
			Widget player1Icon = client.getWidget(BaRoleWidget,player1iconID);
			Widget player2 = client.getWidget(BaRoleWidget,player2ID);
			Widget player2Icon = client.getWidget(BaRoleWidget,player2iconID);
			Widget player3 = client.getWidget(BaRoleWidget,player3ID);
			Widget player3Icon = client.getWidget(BaRoleWidget,player3iconID);
			Widget player4 = client.getWidget(BaRoleWidget, player4ID);
			Widget player4Icon = client.getWidget(BaRoleWidget, player4iconID);
			log.info("Scanning Team");

			if ((player4Icon.getModelId() != leaderIcon.getModelId()) &&  (player4Icon.getModelId() != 65535)){//this number is the blank icon
				log.info("Scanning Complete");
				log.info("Leader is {}", leader.getText());
				log.info("Player1 is {}", player1.getText());
				log.info("Player2 is {}", player2.getText());
				log.info("Player3 is {}", player3.getText());
				log.info("Player4 is {}", player4.getText());
				scanning = false;


				for (int i = 8; i < 12; i++) {
					if (client.getWidget(BaRoleWidget, i).getText().contains(player)){
						//this checks which location the client is in the scroll
					round_roleID = client.getWidget(BaRoleWidget, (i+10)).getModelId();
					round_role = IDfinder(round_roleID);
					log.info("Your role has been identified as {}",round_role);
					}
				}

				if((leaderIcon.getModelId() == attackerIcon)&&(player1Icon.getModelId() == collectorIcon)&&(player2Icon.getModelId() == healerIcon)&&(player4Icon.getModelId() == defenderIcon)){
					round_role = "Leech "+round_role;
					log.info("This has been identified as a leech run as {}",round_role);
					chatMessageManager.queue(QueuedMessage.builder()
							.type(ChatMessageType.CONSOLE)
							.runeLiteFormattedMessage("Leech run identified as " + round_role + " good luck :)")
							.build());
					leech = true;
				}


				if((player.contains(leader.getText()) && leaderIcon.getModelId() == attackerIcon) && !leech){
					round_role = "Main Attacker";
					log.info("You have been identified as Main Attacker");
					rolecurrentpb = getCurrentPB(round_role);
					chatMessageManager.queue(QueuedMessage.builder()
							.type(ChatMessageType.CONSOLE)
							.runeLiteFormattedMessage("Fun run identified as " + round_role + " good luck :)")
							.build());
				}

			}




			}
		}


	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE
			&& event.getMessage().startsWith("---- Wave:"))
		{
			String[] message = event.getMessage().split(" ");
			currentWave = message[BA_WAVE_NUM_INDEX];

			if (currentWave.equals(START_WAVE))
			{
				gameTime = new GameTimer();
			}
		}
	}

	private double getCurrentPB(String pbKey)
	{
		try
		{
			return configManager.getRSProfileConfiguration("BaPB", pbKey, double.class);
		}
		catch (Exception e)
		{
			return 0.0;
		}
	}

	void baLookup(ChatMessage chatMessage, String message)
	{

		ChatMessageType type = chatMessage.getType();
		String search = longBossName(message.substring(BA_COMMAND_STRING.length() + 1));
		final String player;
		if (type.equals(ChatMessageType.PRIVATECHATOUT))
		{
			player = client.getLocalPlayer().getName();
		}
		else
		{
			player = Text.removeTags(chatMessage.getName())
				.replace('\u00A0', ' ');
		}

		net.runelite.http.api.chat.Task task;
		final double BaPb;
		try
		{
			BaPb = chatClient.getPb(player, search);
		}
		catch (IOException ex)
		{
			log.debug("unable to retrieve PB", ex);
			return;
		}
		int minutes = (int) (Math.floor(BaPb) / 60);
		double seconds = BaPb % 60;

		// If the seconds is an integer, it is ambiguous if the pb is a precise
		// pb or not. So we always show it without the trailing .00.
		final String time = Math.floor(seconds) == seconds ?
			String.format("%d:%02d", minutes, (int) seconds) :
			String.format("%d:%05.2f", minutes, seconds);

		String response = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(search)
			.append(ChatColorType.NORMAL)
			.append(" personal best: ")
			.append(ChatColorType.HIGHLIGHT)
			.append(time)
			.build();

		log.debug("Setting response {}", response);
		final MessageNode messageNode = chatMessage.getMessageNode();
		messageNode.setRuneLiteFormatMessage(response);
		chatMessageManager.update(messageNode);
		client.refreshChat();
	}
	private boolean baSubmit(ChatInput chatInput, String value)
	{
		int idx = value.indexOf(' ');
		final String boss = longBossName(value.substring(idx + 1));

		final double pb = configManager.getRSProfileConfiguration("BaPB", boss, double.class);
		if (pb <= 0)
		{
			return false;
		}

		final String playerName = client.getLocalPlayer().getName();

		executor.execute(() ->
		{
			try
			{
				chatClient.submitPb(playerName, boss, pb);
			}
			catch (Exception ex)
			{
				log.warn("unable to submit personal best", ex);
			}
			finally
			{
				chatInput.resume();
			}
		});

		return true;
	}

	private String IDfinder(int roleID){
		if (roleID == attackerIcon) return "Attacker";
		if (roleID == defenderIcon) return "Defender";
		if (roleID == collectorIcon) return "Collector";
		if (roleID == healerIcon) return "Healer";
		return "";
	}


	private static String longBossName(String boss)
	{
		switch (boss.toLowerCase())
		{
			case "att":
			case "a":
			case "main":
				return "Main Attacker";

			case "2a":
				return "Attacker";

			case "la":
				return "Leech Attacker";

			case "heal":
			case "h":
				return "Healer";

			case "lh":
				return "Leech Healer";

			case "def":
			case "d":
				return "Defender";

			case "ld":
				return "Leech Defender";

			case "eggboi":
			case "coll":
			case "col":
			case "c":
				return "Collector";

			case "lc":
				return "Leech Collector";

			case "":
			case " ":
			case "ba":
				return "Barbarian Assault";

			case "r":
				return "Recent";

			default:
				return WordUtils.capitalize(boss);
		}
	}





}