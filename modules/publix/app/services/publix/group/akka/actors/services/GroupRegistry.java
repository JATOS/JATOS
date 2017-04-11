package services.publix.group.akka.actors.services;

import java.util.Collection;
import java.util.Set;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import akka.actor.ActorRef;

/**
 * This class stores the members of a group that is handled by a
 * GroupDispatcher. It's mostly a wrapper around a map that stores the study
 * result IDs together with their corresponding group channels.
 * 
 * This GroupRegistry does not define who is member of a group - it just stores
 * the open group channels. It's possible the a client is a member of a group
 * but currently doesn't have an open channel.
 * 
 * @author Kristian Lange (2016)
 */
public class GroupRegistry {

	/**
	 * Contains the members that are handled by a GroupDispatcher. Maps
	 * StudyResult's IDs <-> ActorRefs. Using a bidirectional map because it's a
	 * one-to-one relationship.
	 */
	private final BidiMap<Long, ActorRef> groupChannelMap = new DualHashBidiMap<>();

	public void register(long studyResultId, ActorRef groupChannel) {
		groupChannelMap.put(studyResultId, groupChannel);
	}

	public void unregister(long studyResultId) {
		groupChannelMap.remove(studyResultId);
	}

	public ActorRef getGroupChannel(long studyResultId) {
		return groupChannelMap.get(studyResultId);
	}

	public boolean isEmpty() {
		return groupChannelMap.isEmpty();
	}

	public boolean containsStudyResult(Long studyResultId) {
		return groupChannelMap.containsKey(studyResultId);
	}

	public Set<Long> getAllStudyResultIds() {
		return groupChannelMap.keySet();
	}

	public Collection<ActorRef> getAllGroupChannels() {
		return groupChannelMap.values();
	}

	public long getStudyResult(ActorRef groupChannel) {
		return groupChannelMap.getKey(groupChannel);
	}

}