package iq.mizan.household.service;

/** Delivers an invitation link to a contact when one was given; the owner also gets the link to share. */
public interface InvitationSender {

    void send(String contact, String householdName, String token);
}
