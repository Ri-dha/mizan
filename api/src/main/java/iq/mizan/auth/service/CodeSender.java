package iq.mizan.auth.service;

import iq.mizan.auth.entity.CodePurpose;

/** Delivers a one-time code over the contact's channel; the provider behind it is swappable. */
public interface CodeSender {

    void send(Identifier contact, CodePurpose purpose, String code);
}
