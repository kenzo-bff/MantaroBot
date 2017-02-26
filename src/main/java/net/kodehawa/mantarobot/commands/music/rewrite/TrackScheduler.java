package net.kodehawa.mantarobot.commands.music.rewrite;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.managers.AudioManager;
import net.kodehawa.mantarobot.MantaroBot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private BlockingQueue<AudioTrackContext> queue;
    private AudioTrackContext previousTrack;
    private AudioTrackContext currentTrack;
    private String lastAnnounce;
    private List<String> voteSkips;
    private Repeat repeat;
    private AudioPlayer audioPlayer;
    private String guildId;

    TrackScheduler(AudioPlayer audioPlayer, String guildId) {
         this.queue = new LinkedBlockingQueue<>();
         this.voteSkips = new ArrayList<>();
         this.previousTrack = null;
         this.currentTrack = null;
         this.lastAnnounce = null;
         this.repeat = null;
         this.audioPlayer = audioPlayer;
         this.guildId = guildId;
    }

    public AudioTrackContext getCurrentTrack() {
        return currentTrack;
    }

    public AudioTrackContext getPreviousTrack() {
        return previousTrack;
    }

    public BlockingQueue<AudioTrackContext> getQueue() {
        return queue;
    }

    public List<String> getVoteSkips() {
        return voteSkips;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public Guild getGuild() {
        return MantaroBot.getJDA().getGuildById(guildId);
    }

    public AudioManager getAudioManager() {
        return getGuild().getAudioManager();
    }

    public void queue(AudioTrackContext audioTrackContext) {
        getQueue().offer(audioTrackContext);
    }

    public boolean isStopped() {
        return getCurrentTrack() == null && getQueue().isEmpty();
    }

    private void announce() {
        try {
            if (getPreviousTrack().getRequestedChannel() != null && getPreviousTrack().getRequestedChannel().canTalk())
                getPreviousTrack().getRequestedChannel().getMessageById(lastAnnounce).complete().delete().queue();
        } catch (Exception ignored) {
        }
        try {
            if (getCurrentTrack().getRequestedChannel() != null && getCurrentTrack().getRequestedChannel().canTalk())
                getCurrentTrack().getRequestedChannel()
                        .sendMessage("\uD83D\uDCE3 Now playing in " + getAudioManager().getConnectedChannel().getName()
                                + ": " + getCurrentTrack().getInfo().title + " (" + AudioUtils.getLength(getCurrentTrack().getInfo().length) + ")"
                                + (getCurrentTrack().getDJ() != null ? " requested by " + getCurrentTrack().getDJ().getName() : "")).queue(this::setLastAnnounce);
        } catch (Exception ignored) {
        }
    }

    private void setLastAnnounce(Message m) {
        this.lastAnnounce = m == null ? null : m.getId();
    }

    public void next(boolean skip) {
        if (repeat == Repeat.SONG && !skip && getCurrentTrack() != null) {
            getAudioPlayer().startTrack(getCurrentTrack().makeClone().getAudioTrack(), false);
            previousTrack = currentTrack;
            currentTrack = queue.poll();
            getAudioPlayer().startTrack(getCurrentTrack().getAudioTrack(), false);
        } else {
            previousTrack = currentTrack;
            currentTrack = queue.poll();
            getAudioPlayer().startTrack(getCurrentTrack().getAudioTrack(), false);
            if (repeat == Repeat.QUEUE)
                queue.offer(previousTrack);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            next(false);
        }
        announce();
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        if (getCurrentTrack().getRequestedChannel() != null && getCurrentTrack().getRequestedChannel().canTalk())
            getCurrentTrack().getRequestedChannel().sendMessage("Something happened while attempting to play " + track.getInfo().title + ": " + exception.getMessage() + " (Severity: " + exception.severity + ")").queue();
        next(true);
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        if (getCurrentTrack().getRequestedChannel() != null && getCurrentTrack().getRequestedChannel().canTalk())
            getCurrentTrack().getRequestedChannel().sendMessage("Track got stuck, skipping...").queue();
        next(true);
    }
}