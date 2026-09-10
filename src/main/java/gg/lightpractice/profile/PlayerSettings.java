package gg.lightpractice.profile;

import gg.lightpractice.model.ChatChannel;

/** Toggleable per player preferences stored on the profile. */
public final class PlayerSettings {

    private boolean scoreboardVisible = true;
    private boolean receiveDuelRequests = true;
    private boolean allowSpectators = true;
    private boolean allowPartyInvites = true;
    private boolean cosmeticEffects = true;
    private boolean globalChat = true;
    private ChatChannel chatChannel = ChatChannel.PUBLIC;

    public boolean scoreboardVisible() {
        return scoreboardVisible;
    }

    public void scoreboardVisible(boolean value) {
        this.scoreboardVisible = value;
    }

    public boolean receiveDuelRequests() {
        return receiveDuelRequests;
    }

    public void receiveDuelRequests(boolean value) {
        this.receiveDuelRequests = value;
    }

    public boolean allowSpectators() {
        return allowSpectators;
    }

    public void allowSpectators(boolean value) {
        this.allowSpectators = value;
    }

    public boolean allowPartyInvites() {
        return allowPartyInvites;
    }

    public void allowPartyInvites(boolean value) {
        this.allowPartyInvites = value;
    }

    public boolean cosmeticEffects() {
        return cosmeticEffects;
    }

    public void cosmeticEffects(boolean value) {
        this.cosmeticEffects = value;
    }

    public boolean globalChat() {
        return globalChat;
    }

    public void globalChat(boolean value) {
        this.globalChat = value;
    }

    public ChatChannel chatChannel() {
        return chatChannel;
    }

    public void chatChannel(ChatChannel channel) {
        this.chatChannel = channel == null ? ChatChannel.PUBLIC : channel;
    }

    public PlayerSettings copy() {
        PlayerSettings copy = new PlayerSettings();
        copy.scoreboardVisible = scoreboardVisible;
        copy.receiveDuelRequests = receiveDuelRequests;
        copy.allowSpectators = allowSpectators;
        copy.allowPartyInvites = allowPartyInvites;
        copy.cosmeticEffects = cosmeticEffects;
        copy.globalChat = globalChat;
        copy.chatChannel = chatChannel;
        return copy;
    }

    public void loadFrom(PlayerSettings other) {
        if (other == null) {
            return;
        }
        this.scoreboardVisible = other.scoreboardVisible;
        this.receiveDuelRequests = other.receiveDuelRequests;
        this.allowSpectators = other.allowSpectators;
        this.allowPartyInvites = other.allowPartyInvites;
        this.cosmeticEffects = other.cosmeticEffects;
        this.globalChat = other.globalChat;
        this.chatChannel = other.chatChannel;
    }
}
