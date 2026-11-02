package com.mystaria.events.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * A player clicked an event's trainer NPC: the challenge, with the Ghost team or the Cobblemon party (Adrien
 * 2026-11-02), then a Ghost battle against the NPC's team, played by its AI.
 */
public final class NpcChallengeScreen extends Screen {

	private final EventNpcDto npc;
	private Component message;

	public NpcChallengeScreen(EventNpcDto npc) {
		super(Component.translatable("phantasmon_events.npc.challenge_title", npc.name()));
		this.npc = npc;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int y = height / 2;
		addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.npc.with_ghost"), b -> start(false))
				.bounds(left, y, 152, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("phantasmon_events.npc.with_cobblemon"), b -> start(true))
				.bounds(left + 158, y, 152, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 - 75, y + 26, 150, 20).build());
	}

	private void start(boolean cobblemon) {
		if (!PhantasmonApi.startNpcBattle(npc.uuid(), cobblemon)) {
			message = Component.translatable(cobblemon ? "phantasmon_events.npc.no_party" : "phantasmon_events.error.network")
					.withStyle(ChatFormatting.RED);
			return;
		}
		onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int y = height / 2 - 50;
		g.drawCenteredString(font, Component.translatable("phantasmon_events.npc.challenge", npc.name()).withStyle(ChatFormatting.GOLD), width / 2, y, 0xFFFFFFFF);
		g.drawCenteredString(font, Component.translatable("phantasmon_events.npc.challenge_info", npc.teamSize(), NpcEditScreen.aiName(npc.ai())),
				width / 2, y + 16, 0xFFC0C0C0);
		if (message != null) {
			g.drawCenteredString(font, message, width / 2, height / 2 + 56, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
