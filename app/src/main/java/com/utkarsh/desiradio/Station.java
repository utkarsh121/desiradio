package com.utkarsh.desiradio;

public class Station {
    public final String name;
    public final String streamUrl;
    public final String language;
    public final String genre;
    public final String logoUrl;

    public Station(String name, String streamUrl, String language, String genre, String logoUrl) {
        this.name = name;
        this.streamUrl = streamUrl;
        this.language = language;
        this.genre = genre;
        this.logoUrl = logoUrl == null ? "" : logoUrl;
    }
}
