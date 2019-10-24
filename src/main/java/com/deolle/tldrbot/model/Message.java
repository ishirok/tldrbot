package com.deolle.tldrbot.model;

public class Message {

    private Integer message_id;
    private User from;
    private Integer date;
    private User chat;
    private User forward_from;
    private Integer forward_date;
    private Message reply_to_message;
    private String text;
    private Object audio;
    private Object document;
    private Object photo;
    private Object sticker;
    private Object video;
    private Object contact;
    private Object location;
    private User new_chat_participant;
    private User left_chat_participant;
    private String new_chat_title;
    private Object new_chat_photo;
    private Boolean delete_chat_photo;
    private Boolean group_chat_created;
    private String caption;


    public Integer getMessage_id() {
        return message_id;
    }

    public void setMessage_id(Integer message_id) {
        this.message_id = message_id;
    }

    public User getFrom() {
        return from;
    }

    public void setFrom(User from) {
        this.from = from;
    }

    public Integer getDate() {
        return date;
    }

    public void setDate(Integer date) {
        this.date = date;
    }

    public User getChat() {
        return chat;
    }

    public void setChat(User chat) {
        this.chat = chat;
    }

    public User getForward_from() {
        return forward_from;
    }

    public void setForward_from(User forward_from) {
        this.forward_from = forward_from;
    }

    public Integer getForward_date() {
        return forward_date;
    }

    public void setForward_date(Integer forward_date) {
        this.forward_date = forward_date;
    }

    public Message getReply_to_message() {
        return reply_to_message;
    }

    public void setReply_to_message(Message reply_to_message) {
        this.reply_to_message = reply_to_message;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Object getAudio() {
        return audio;
    }

    public void setAudio(Object audio) {
        this.audio = audio;
    }

    public Object getDocument() {
        return document;
    }

    public void setDocument(Object document) {
        this.document = document;
    }

    public Object getPhoto() {
        return photo;
    }

    public void setPhoto(Object photo) {
        this.photo = photo;
    }

    public Object getSticker() {
        return sticker;
    }

    public void setSticker(Object sticker) {
        this.sticker = sticker;
    }

    public Object getVideo() {
        return video;
    }

    public void setVideo(Object video) {
        this.video = video;
    }

    public Object getContact() {
        return contact;
    }

    public void setContact(Object contact) {
        this.contact = contact;
    }

    public Object getLocation() {
        return location;
    }

    public void setLocation(Object location) {
        this.location = location;
    }

    public User getNew_chat_participant() {
        return new_chat_participant;
    }

    public void setNew_chat_participant(User new_chat_participant) {
        this.new_chat_participant = new_chat_participant;
    }

    public User getLeft_chat_participant() {
        return left_chat_participant;
    }

    public void setLeft_chat_participant(User left_chat_participant) {
        this.left_chat_participant = left_chat_participant;
    }

    public String getNew_chat_title() {
        return new_chat_title;
    }

    public void setNew_chat_title(String new_chat_title) {
        this.new_chat_title = new_chat_title;
    }

    public Object getNew_chat_photo() {
        return new_chat_photo;
    }

    public void setNew_chat_photo(Object new_chat_photo) {
        this.new_chat_photo = new_chat_photo;
    }

    public Boolean getDelete_chat_photo() {
        return delete_chat_photo;
    }

    public void setDelete_chat_photo(Boolean delete_chat_photo) {
        this.delete_chat_photo = delete_chat_photo;
    }

    public Boolean getGroup_chat_created() {
        return group_chat_created;
    }

    public void setGroup_chat_created(Boolean group_chat_created) {
        this.group_chat_created = group_chat_created;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }
}
