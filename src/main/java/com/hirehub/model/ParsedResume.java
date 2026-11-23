package com.hirehub.model;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

/**
 * ParsedResume — Structured representation of data extracted from a PDF resume.
 */
public class ParsedResume {

    private String       name;
    private String       email;
    private String       phone;
    private String       college;
    private String       degree;
    private int          graduationYear;
    private List<String> skills           = new ArrayList<>();
    private List<String> workExperiences  = new ArrayList<>();
    private List<String> projects         = new ArrayList<>();
    private List<String> certifications   = new ArrayList<>();
    private List<String> links            = new ArrayList<>();
    private int          metricsCount;    // count of numbers / % / quantifiable results found
    private String       rawText;

    public ParsedResume() {}

    // ── Getters and Setters ─────────────────────────────────────────────────

    public String getName()                                    { return name; }
    public void setName(String name)                           { this.name = name; }

    public String getEmail()                                   { return email; }
    public void setEmail(String email)                         { this.email = email; }

    public String getPhone()                                   { return phone; }
    public void setPhone(String phone)                         { this.phone = phone; }

    public String getCollege()                                 { return college; }
    public void setCollege(String college)                     { this.college = college; }

    public String getDegree()                                  { return degree; }
    public void setDegree(String degree)                       { this.degree = degree; }

    public int getGraduationYear()                             { return graduationYear; }
    public void setGraduationYear(int year)                    { this.graduationYear = year; }

    public List<String> getSkills()                            { return skills; }
    public void setSkills(List<String> skills)                 { this.skills = (skills != null) ? skills : new ArrayList<>(); }

    public List<String> getWorkExperiences()                   { return workExperiences; }
    public void setWorkExperiences(List<String> experiences)   { this.workExperiences = (experiences != null) ? experiences : new ArrayList<>(); }

    public List<String> getProjects()                          { return projects; }
    public void setProjects(List<String> projects)             { this.projects = (projects != null) ? projects : new ArrayList<>(); }

    public List<String> getCertifications()                    { return certifications; }
    public void setCertifications(List<String> certifications) { this.certifications = (certifications != null) ? certifications : new ArrayList<>(); }

    public List<String> getLinks()                             { return links; }
    public void setLinks(List<String> links)                   { this.links = (links != null) ? links : new ArrayList<>(); }

    public int getMetricsCount()                               { return metricsCount; }
    public void setMetricsCount(int count)                     { this.metricsCount = count; }

    public String getRawText()                                 { return rawText; }
    public void setRawText(String text)                        { this.rawText = text; }

    // ── Utilities ───────────────────────────────────────────────────────────

    public String getSkillsCsv() {
        if (skills == null || skills.isEmpty()) return "";
        return String.join(", ", skills);
    }

    public String toJson(Gson gson) {
        return gson.toJson(this);
    }

    public static ParsedResume fromJson(String json, Gson gson) {
        if (json == null || json.isBlank()) return new ParsedResume();
        try {
            return gson.fromJson(json, ParsedResume.class);
        } catch (Exception e) {
            return new ParsedResume();
        }
    }
}

