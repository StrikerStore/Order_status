package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class BotspaceMessageRequest {
    
    @JsonProperty("phone")
    private String phone;
    
    @JsonProperty("templateId")
    private String templateId;
    
    @JsonProperty("variables")
    private List<String> variables;
    
    @JsonProperty("cards")
    private List<Card> cards;
    
    @JsonProperty("mediaVariable")
    private String mediaVariable;

    public BotspaceMessageRequest() {
    }

    public BotspaceMessageRequest(String phone, String templateId, List<String> variables) {
        this.phone = phone;
        this.templateId = templateId;
        this.variables = variables;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public List<String> getVariables() {
        return variables;
    }

    public void setVariables(List<String> variables) {
        this.variables = variables;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    public String getMediaVariable() {
        return mediaVariable;
    }

    public void setMediaVariable(String mediaVariable) {
        this.mediaVariable = mediaVariable;
    }

    /**
     * Card structure for media variables
     */
    public static class Card {
        @JsonProperty("variables")
        private List<String> variables;
        
        @JsonProperty("mediaVariable")
        private String mediaVariable;

        public Card() {
        }

        public Card(String mediaVariable) {
            this.mediaVariable = mediaVariable;
            this.variables = List.of();
        }

        public List<String> getVariables() {
            return variables;
        }

        public void setVariables(List<String> variables) {
            this.variables = variables;
        }

        public String getMediaVariable() {
            return mediaVariable;
        }

        public void setMediaVariable(String mediaVariable) {
            this.mediaVariable = mediaVariable;
        }
    }
}
