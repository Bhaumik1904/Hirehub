package com.hirehub.service;

import com.hirehub.model.ParsedResume;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ResumeParserService — Uses Apache PDFBox to validate and extract structured data
 * from student PDF resumes.
 */
public class ResumeParserService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    // Regex patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:(?:\\+|0{0,2})91[\\s-]?)?(?:[6-9]\\d{9}|\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4})\\b"
    );

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(github\\.com/[a-zA-Z0-9_-]+|linkedin\\.com/in/[a-zA-Z0-9_%-]+|[a-zA-Z0-9_-]+\\.github\\.io|[a-zA-Z0-9_-]+\\.vercel\\.app|[a-zA-Z0-9_-]+\\.netlify\\.app)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GRAD_YEAR_PATTERN = Pattern.compile(
            "\\b(20(?:1[5-9]|2[0-9]|3[0-5]))\\b"
    );

    private static final Pattern DEGREE_PATTERN = Pattern.compile(
            "\\b(B\\.?Tech(?:nology)?|B\\.?E\\.?|Bachelor of (?:Technology|Engineering|Science)|B\\.?Sc\\.?|BCA|M\\.?Tech|M\\.?E\\.?|Master of (?:Technology|Science|Computer Applications)|M\\.?Sc\\.?|MCA|MBA|Ph\\.?D)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COLLEGE_PATTERN = Pattern.compile(
            "([A-Z][A-Za-z0-9&,.'\\s]+(?:University|Institute of Technology|College of Engineering|Engineering College|Institute of Science|Academy|Campus|Polytechnic|IIT|NIT|IIIT|BITS|VIT))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern METRICS_PATTERN = Pattern.compile(
            "(?:\\b\\d+(?:\\.\\d+)?%|\\b\\d+\\+?\\s*(?:users|clients|requests|qps|ms|seconds|stars|downloads|commits|projects|members|students)\\b|\\b(?:reduced|improved|increased|boosted)\\s+by\\s+\\d+|\\$\\d+[KkMm]?\\b)",
            Pattern.CASE_INSENSITIVE
    );

    // Tech skills dictionary
    private static final Set<String> KNOWN_SKILLS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    static {
        // Languages
        Collections.addAll(KNOWN_SKILLS,
                "Java", "Python", "JavaScript", "TypeScript", "C", "C++", "C#", "Go", "Golang",
                "Rust", "Kotlin", "Swift", "PHP", "Ruby", "R", "Dart", "Scala", "SQL", "Bash", "Shell"
        );
        // Frontend
        Collections.addAll(KNOWN_SKILLS,
                "React", "React.js", "Angular", "Vue", "Vue.js", "Next.js", "HTML", "HTML5", "CSS", "CSS3",
                "Tailwind", "Tailwind CSS", "Bootstrap", "Redux", "JavaFX", "jQuery", "Sass", "Webpack", "Vite"
        );
        // Backend & Frameworks
        Collections.addAll(KNOWN_SKILLS,
                "Spring", "Spring Boot", "Node.js", "Express", "Express.js", "Django", "FastAPI",
                "Flask", "ASP.NET", ".NET", "Hibernate", "JPA", "REST", "REST API", "GraphQL", "gRPC"
        );
        // Databases & Storage
        Collections.addAll(KNOWN_SKILLS,
                "MySQL", "PostgreSQL", "SQLite", "MongoDB", "Redis", "Cassandra", "Oracle",
                "Elasticsearch", "Firebase", "DynamoDB", "BigQuery", "MariaDB"
        );
        // Cloud & DevOps
        Collections.addAll(KNOWN_SKILLS,
                "AWS", "Amazon Web Services", "GCP", "Google Cloud", "Azure", "Docker", "Kubernetes",
                "CI/CD", "GitHub Actions", "Jenkins", "Linux", "Terraform", "Git", "GitHub", "GitLab"
        );
        // Data & AI / ML
        Collections.addAll(KNOWN_SKILLS,
                "Machine Learning", "Deep Learning", "Data Science", "Pandas", "NumPy", "Scikit-Learn",
                "TensorFlow", "PyTorch", "Keras", "OpenCV", "NLP", "LLM", "Generative AI", "Computer Vision"
        );
        // Concepts & Tools
        Collections.addAll(KNOWN_SKILLS,
                "Data Structures", "Algorithms", "Object-Oriented Programming", "OOP", "System Design",
                "Microservices", "Design Patterns", "Agile", "Scrum", "Postman", "Maven", "Gradle", "JUnit"
        );
    }

    // ── Validation ──────────────────────────────────────────────────────────

    public static class ValidationResult {
        private final boolean valid;
        private final String  errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult ok()                 { return new ValidationResult(true, null); }
        public static ValidationResult fail(String message) { return new ValidationResult(false, message); }

        public boolean isValid()         { return valid; }
        public String  getErrorMessage() { return errorMessage; }
    }

    /**
     * Validates that the file exists, is non-empty, <= 5MB, has %PDF- signature,
     * is not encrypted, and can be opened as a valid PDF.
     */
    public ValidationResult validatePdfFile(File file) {
        if (file == null || !file.exists()) {
            return ValidationResult.fail("File does not exist.");
        }
        if (!file.isFile()) {
            return ValidationResult.fail("Target is not a regular file.");
        }
        if (file.length() == 0) {
            return ValidationResult.fail("File is empty (0 bytes).");
        }
        if (file.length() > MAX_FILE_SIZE_BYTES) {
            return ValidationResult.fail(String.format("File size exceeds 5 MB limit (Current: %.2f MB).",
                    file.length() / (1024.0 * 1024.0)));
        }

        // Header check: %PDF-
        try (InputStream in = new FileInputStream(file)) {
            byte[] header = new byte[5];
            int read = in.read(header);
            if (read < 5 || !"%PDF-".equals(new String(header, 0, 5))) {
                return ValidationResult.fail("Invalid file format. The file is not a valid PDF document (%PDF- header missing).");
            }
        } catch (IOException e) {
            return ValidationResult.fail("Could not read file header: " + e.getMessage());
        }

        // PDFBox parsing and encryption check
        try (PDDocument doc = PDDocument.load(file)) {
            if (doc.isEncrypted()) {
                return ValidationResult.fail("The PDF is password protected or encrypted. Please upload an unlocked PDF.");
            }
            if (doc.getNumberOfPages() < 1) {
                return ValidationResult.fail("The PDF has no pages.");
            }
        } catch (IOException e) {
            return ValidationResult.fail("Corrupted or unreadable PDF: " + e.getMessage());
        }

        return ValidationResult.ok();
    }

    // ── Extraction ──────────────────────────────────────────────────────────

    /**
     * Extracts text from the PDF file and produces a structured ParsedResume.
     */
    public ParsedResume parseResume(File pdfFile) throws IOException {
        ValidationResult val = validatePdfFile(pdfFile);
        if (!val.isValid()) {
            throw new IllegalArgumentException(val.getErrorMessage());
        }

        String rawText;
        try (PDDocument doc = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            rawText = stripper.getText(doc);
        }

        if (rawText == null || rawText.isBlank()) {
            ParsedResume empty = new ParsedResume();
            empty.setRawText("");
            return empty;
        }

        return parseExtractedText(rawText);
    }

    /**
     * Parses raw text into structured fields.
     */
    public ParsedResume parseExtractedText(String text) {
        ParsedResume res = new ParsedResume();
        res.setRawText(text);

        String[] lines = text.split("\\r?\\n");
        List<String> nonEmptyLines = new ArrayList<>();
        for (String l : lines) {
            String tr = l.trim();
            if (!tr.isEmpty()) {
                nonEmptyLines.add(tr);
            }
        }

        // 1. Candidate Name (Heuristic: top non-empty line before contact info)
        String candidateName = extractCandidateName(nonEmptyLines);
        res.setName(candidateName);

        // 2. Email
        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        if (emailMatcher.find()) {
            res.setEmail(emailMatcher.group(0));
        }

        // 3. Phone
        Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
        if (phoneMatcher.find()) {
            res.setPhone(phoneMatcher.group(0).trim());
        }

        // 4. Links (GitHub, LinkedIn, Portfolios)
        Matcher linkMatcher = LINK_PATTERN.matcher(text);
        Set<String> linkSet = new LinkedHashSet<>();
        while (linkMatcher.find()) {
            String link = linkMatcher.group(0);
            if (!link.startsWith("http://") && !link.startsWith("https://")) {
                link = "https://" + link;
            }
            linkSet.add(link);
        }
        res.setLinks(new ArrayList<>(linkSet));

        // 5. Degree
        Matcher degreeMatcher = DEGREE_PATTERN.matcher(text);
        if (degreeMatcher.find()) {
            res.setDegree(degreeMatcher.group(1).trim());
        }

        // 6. College / University
        Matcher collegeMatcher = COLLEGE_PATTERN.matcher(text);
        if (collegeMatcher.find()) {
            res.setCollege(collegeMatcher.group(1).trim());
        }

        // 7. Graduation Year
        Matcher gradMatcher = GRAD_YEAR_PATTERN.matcher(text);
        int latestYear = 0;
        while (gradMatcher.find()) {
            try {
                int yr = Integer.parseInt(gradMatcher.group(1));
                if (yr > latestYear) {
                    latestYear = yr;
                }
            } catch (NumberFormatException ignored) {}
        }
        if (latestYear > 0) {
            res.setGraduationYear(latestYear);
        }

        // 8. Skills Extraction (Lexicon match + Skills Section extraction)
        Set<String> skillsSet = new LinkedHashSet<>();

        // Section extraction for skills
        String skillsSection = extractSection(text, "SKILLS", "TECHNICAL SKILLS", "KEY SKILLS", "AREAS OF EXPERTISE");
        if (skillsSection != null && !skillsSection.isBlank()) {
            String[] splitTokens = skillsSection.split("[,;•|/\\n\\r]+");
            for (String tok : splitTokens) {
                String clean = tok.trim().replaceAll("^[\\-*•]\\s*", "");
                if (clean.length() >= 2 && clean.length() <= 30 && !clean.contains(":")) {
                    for (String known : KNOWN_SKILLS) {
                        if (known.equalsIgnoreCase(clean)) {
                            skillsSet.add(known);
                            break;
                        }
                    }
                }
            }
        }

        // Whole document scan for known skills
        for (String skill : KNOWN_SKILLS) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(skill) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) {
                skillsSet.add(skill);
            }
        }
        res.setSkills(new ArrayList<>(skillsSet));

        // 9. Work Experience / Internships Section
        String expSection = extractSection(text, "EXPERIENCE", "WORK EXPERIENCE", "EMPLOYMENT HISTORY", "INTERNSHIP", "INTERNSHIPS");
        if (expSection != null && !expSection.isBlank()) {
            List<String> expItems = splitIntoBulletOrParagraphItems(expSection);
            res.setWorkExperiences(expItems);
        }

        // 10. Projects Section
        String projSection = extractSection(text, "PROJECTS", "ACADEMIC PROJECTS", "KEY PROJECTS", "PERSONAL PROJECTS");
        if (projSection != null && !projSection.isBlank()) {
            List<String> projItems = splitIntoBulletOrParagraphItems(projSection);
            res.setProjects(projItems);
        }

        // 11. Certifications Section
        String certSection = extractSection(text, "CERTIFICATIONS", "CERTIFICATES", "LICENSES & CERTIFICATIONS", "COURSES");
        if (certSection != null && !certSection.isBlank()) {
            List<String> certItems = splitIntoBulletOrParagraphItems(certSection);
            res.setCertifications(certItems);
        }

        // 12. Quantifiable metrics count
        Matcher metricMatcher = METRICS_PATTERN.matcher(text);
        int metricCount = 0;
        while (metricMatcher.find()) {
            metricCount++;
        }
        res.setMetricsCount(metricCount);

        return res;
    }

    // ── Helper Heuristics ───────────────────────────────────────────────────

    private String extractCandidateName(List<String> lines) {
        if (lines.isEmpty()) return "Student Candidate";

        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            String line = lines.get(i).trim();
            // Skip lines with email, URLs, or headers like "Resume" / "Curriculum Vitae"
            if (EMAIL_PATTERN.matcher(line).find()) continue;
            if (line.toLowerCase().contains("resume") || line.toLowerCase().contains("curriculum vitae")) continue;
            if (line.toLowerCase().contains("phone") || line.toLowerCase().contains("email")) continue;
            if (line.matches(".*\\d{5,}.*")) continue; // skip numbers/addresses

            // Name should be between 2 and 40 characters and start with an uppercase letter
            if (line.length() >= 2 && line.length() <= 40 && Character.isUpperCase(line.charAt(0))) {
                // Remove trailing titles or phone numbers if on same line
                String cleaned = line.split("[,|•]")[0].trim();
                if (cleaned.length() >= 2 && cleaned.matches("[A-Za-z\\s.'-]+")) {
                    return cleaned;
                }
            }
        }
        return "Student Candidate";
    }

    private String extractSection(String fullText, String... headerAliases) {
        String[] allSectionHeaders = {
                "EDUCATION", "SKILLS", "TECHNICAL SKILLS", "KEY SKILLS", "AREAS OF EXPERTISE",
                "EXPERIENCE", "WORK EXPERIENCE", "EMPLOYMENT HISTORY", "INTERNSHIP", "INTERNSHIPS",
                "PROJECTS", "ACADEMIC PROJECTS", "KEY PROJECTS", "PERSONAL PROJECTS",
                "CERTIFICATIONS", "CERTIFICATES", "LICENSES & CERTIFICATIONS", "COURSES",
                "PUBLICATIONS", "ACHIEVEMENTS", "HONORS", "LANGUAGES", "DECLARATION", "HOBBIES"
        };

        for (String targetHeader : headerAliases) {
            Pattern startPattern = Pattern.compile("(?:^|\\n)\\s*" + Pattern.quote(targetHeader) + "\\s*[:\\-]?\\s*(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
            Matcher startMatcher = startPattern.matcher(fullText);
            if (startMatcher.find()) {
                int startIdx = startMatcher.end();

                // Find next section header
                int endIdx = fullText.length();
                for (String nextHeader : allSectionHeaders) {
                    if (Arrays.stream(headerAliases).anyMatch(h -> h.equalsIgnoreCase(nextHeader))) {
                        continue;
                    }
                    Pattern nextPattern = Pattern.compile("(?:^|\\n)\\s*" + Pattern.quote(nextHeader) + "\\s*[:\\-]?\\s*(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
                    Matcher nextMatcher = nextPattern.matcher(fullText);
                    if (nextMatcher.find(startIdx)) {
                        if (nextMatcher.start() < endIdx) {
                            endIdx = nextMatcher.start();
                        }
                    }
                }
                String section = fullText.substring(startIdx, endIdx).trim();
                if (!section.isEmpty()) {
                    return section;
                }
            }
        }
        return null;
    }

    private List<String> splitIntoBulletOrParagraphItems(String sectionText) {
        List<String> items = new ArrayList<>();
        String[] rawLines = sectionText.split("\\r?\\n");
        StringBuilder currentItem = new StringBuilder();

        for (String line : rawLines) {
            String tr = line.trim();
            if (tr.isEmpty()) {
                if (currentItem.length() > 0) {
                    items.add(currentItem.toString().trim());
                    currentItem.setLength(0);
                }
                continue;
            }

            // Check if line starts with bullet or number
            if (tr.matches("^[\\-*•·▪]\\s+.*") || tr.matches("^\\d+[\\.\\)]\\s+.*")) {
                if (currentItem.length() > 0) {
                    items.add(currentItem.toString().trim());
                    currentItem.setLength(0);
                }
                currentItem.append(tr.replaceAll("^[\\-*•·▪\\d\\.\\)]+\\s*", ""));
            } else {
                if (currentItem.length() > 0) {
                    currentItem.append(" ").append(tr);
                } else {
                    currentItem.append(tr);
                }
            }
        }

        if (currentItem.length() > 0) {
            items.add(currentItem.toString().trim());
        }

        return items;
    }
}
