package com.fitmeet.briefing.generation;

/** Synchronous model port. Only the generation worker should invoke it. */
public interface BriefingModel {
    String complete(String systemInstruction, String evidenceInput);
}
