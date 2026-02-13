package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BotspaceMessageResponse {

    /**
     * Current Botspace format: { "data": { "id", "conversationId", "status":
     * "accepted" } }
     */
    @JsonProperty("data")
    private Data data;

    public BotspaceMessageResponse() {
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    /**
     * True when message was accepted (current format: data.status = "accepted" or
     * legacy success = true).
     */
    @JsonIgnore
    public boolean isAccepted() {
        if (data != null && "accepted".equalsIgnoreCase(data.getStatus())) {
            return true;
        }
        return false;
    }

    public static class Data {
        @JsonProperty("id")
        private String id;

        @JsonProperty("conversationId")
        private String conversationId;

        @JsonProperty("status")
        private String status;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
