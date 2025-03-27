package org.egov.transformer.models.expense;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.Document;
import lombok.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;


@Data
public class Workflow {
    @JsonProperty("action")
    private String action = null;
    @JsonProperty("comments")
    private String comments = null;
    @JsonProperty("documents")
    private @Valid List<Document> documents = null;
    @JsonProperty("assignes")
    private @Valid List<String> assignes = null;
    @JsonProperty("rating")
    private Integer rating = null;

    public Workflow addDocumentsItem(Document documentsItem) {
        if (this.documents == null) {
            this.documents = new ArrayList();
        }

        this.documents.add(documentsItem);
        return this;
    }

    public static WorkflowBuilder builder() {
        return new WorkflowBuilder();
    }

    @JsonProperty("action")
    public void setAction(final String action) {
        this.action = action;
    }

    @JsonProperty("comments")
    public void setComments(final String comments) {
        this.comments = comments;
    }

    @JsonProperty("documents")
    public void setDocuments(final List<Document> documents) {
        this.documents = documents;
    }

    @JsonProperty("assignes")
    public void setAssignes(final List<String> assignes) {
        this.assignes = assignes;
    }

    @JsonProperty("rating")
    public void setRating(final Integer rating) {
        this.rating = rating;
    }

    public Workflow(final String action, final String comments, final List<Document> documents, final List<String> assignes, final Integer rating) {
        this.action = action;
        this.comments = comments;
        this.documents = documents;
        this.assignes = assignes;
        this.rating = rating;
    }

    public Workflow() {
    }

    public static class WorkflowBuilder {
        private String action;
        private String comments;
        private List<Document> documents;
        private List<String> assignes;
        private Integer rating;

        WorkflowBuilder() {
        }

        @JsonProperty("action")
        public WorkflowBuilder action(final String action) {
            this.action = action;
            return this;
        }

        @JsonProperty("comments")
        public WorkflowBuilder comments(final String comments) {
            this.comments = comments;
            return this;
        }

        @JsonProperty("documents")
        public WorkflowBuilder documents(final List<Document> documents) {
            this.documents = documents;
            return this;
        }

        @JsonProperty("assignes")
        public WorkflowBuilder assignes(final List<String> assignes) {
            this.assignes = assignes;
            return this;
        }

        @JsonProperty("rating")
        public WorkflowBuilder rating(final Integer rating) {
            this.rating = rating;
            return this;
        }

        public Workflow build() {
            return new Workflow(this.action, this.comments, this.documents, this.assignes, this.rating);
        }

        public String toString() {
            return "Workflow.WorkflowBuilder(action=" + this.action + ", comments=" + this.comments + ", documents=" + this.documents + ", assignes=" + this.assignes + ", rating=" + this.rating + ")";
        }
    }
}
