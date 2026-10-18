import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommunityHelpHub extends JFrame {

    private static final String API =
            "http://127.0.0.1:8000/api";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final JTextField titleField = new JTextField();
    private final JTextField locationField = new JTextField();
    private final JTextField contactField = new JTextField();
    private final JTextField searchField = new JTextField();

    private final JTextArea descriptionArea = new JTextArea(4, 30);

    private final JComboBox<String> categoryBox =
            new JComboBox<>(new String[]{
                    "Food",
                    "Education",
                    "Healthcare",
                    "Shelter",
                    "Employment",
                    "Other"
            });

    private final JComboBox<String> filterBox =
            new JComboBox<>(new String[]{
                    "All categories",
                    "Food",
                    "Education",
                    "Healthcare",
                    "Shelter",
                    "Employment",
                    "Other"
            });

    private final DefaultListModel<HelpRequest> listModel =
            new DefaultListModel<>();

    private final JList<HelpRequest> requestList =
            new JList<>(listModel);

    private final JTextArea detailsArea = new JTextArea();

    private final JLabel statusLabel =
            new JLabel("Connecting to the local service...");

    public CommunityHelpHub() {
        setTitle("Community Help Hub");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);

        buildInterface();
        refreshRequests();
    }

    private void buildInterface() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel heading = new JLabel("Community Help Hub");
        heading.setFont(new Font("SansSerif", Font.BOLD, 26));

        JLabel subtitle = new JLabel(
                "Share resources. Request help. Support your community."
        );

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 5));
        header.add(heading);
        header.add(subtitle);

        root.add(header, BorderLayout.NORTH);

        JPanel leftPanel = buildFormPanel();
        JPanel rightPanel = buildRequestsPanel();

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                rightPanel
        );

        splitPane.setResizeWeight(0.42);
        root.add(splitPane, BorderLayout.CENTER);

        statusLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JLabel heading = new JLabel("Create a help request");
        heading.setFont(new Font("SansSerif", Font.BOLD, 18));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);

        addField(form, gbc, "Request title", titleField);
        addField(form, gbc, "Category", categoryBox);
        addField(form, gbc, "Location", locationField);
        addField(form, gbc, "Contact information", contactField);

        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        gbc.gridy++;
        form.add(new JLabel("Description"), gbc);

        gbc.gridy++;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;

        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setPreferredSize(new Dimension(250, 120));
        form.add(descriptionScroll, gbc);

        JButton submitButton = new JButton("Submit request");
        submitButton.addActionListener(event -> submitRequest());

        JButton clearButton = new JButton("Clear form");
        clearButton.addActionListener(event -> clearForm());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
        buttons.add(submitButton);
        buttons.add(clearButton);

        panel.add(heading, BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private void addField(
            JPanel panel,
            GridBagConstraints gbc,
            String label,
            JComponent field
    ) {
        gbc.gridy++;
        panel.add(new JLabel(label), gbc);

        gbc.gridy++;
        field.setPreferredSize(new Dimension(250, 30));
        panel.add(field, gbc);
    }

    private JPanel buildRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JLabel heading = new JLabel("Community requests");
        heading.setFont(new Font("SansSerif", Font.BOLD, 18));

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(event -> refreshRequests());

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> refreshRequests());

        searchField.setToolTipText("Search title, description, or location");

        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        JPanel filterPanel = new JPanel(new BorderLayout(5, 5));
        filterPanel.add(filterBox, BorderLayout.CENTER);
        filterPanel.add(refreshButton, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.add(heading, BorderLayout.NORTH);

        JPanel filters = new JPanel(new GridLayout(2, 1, 0, 5));
        filters.add(searchPanel);
        filters.add(filterPanel);

        top.add(filters, BorderLayout.CENTER);

        requestList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        requestList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
            ) {
                JLabel label = (JLabel) super
                        .getListCellRendererComponent(
                                list,
                                value,
                                index,
                                isSelected,
                                cellHasFocus
                        );

                if (value instanceof HelpRequest request) {
                    label.setText(
                            "<html><b>" + escapeHtml(request.title)
                                    + "</b><br>"
                                    + escapeHtml(request.category)
                                    + " | "
                                    + escapeHtml(request.status)
                                    + "<br>"
                                    + escapeHtml(request.location)
                                    + "</html>"
                    );

                    label.setBorder(new EmptyBorder(8, 8, 8, 8));
                }

                return label;
            }
        });

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(new Font("SansSerif", Font.PLAIN, 13));

        requestList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelectedRequest();
            }
        });

        JScrollPane listScroll = new JScrollPane(requestList);
        JScrollPane detailsScroll = new JScrollPane(detailsArea);

        JSplitPane contentSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                listScroll,
                detailsScroll
        );

        contentSplit.setResizeWeight(0.6);

        JButton progressButton = new JButton("Mark In Progress");
        progressButton.addActionListener(
                event -> updateSelectedStatus("In Progress")
        );

        JButton resolvedButton = new JButton("Mark Resolved");
        resolvedButton.addActionListener(
                event -> updateSelectedStatus("Resolved")
        );

        JButton reopenButton = new JButton("Reopen");
        reopenButton.addActionListener(
                event -> updateSelectedStatus("Open")
        );

        JPanel actions = new JPanel(new GridLayout(1, 3, 5, 0));
        actions.add(progressButton);
        actions.add(resolvedButton);
        actions.add(reopenButton);

        panel.add(top, BorderLayout.NORTH);
        panel.add(contentSplit, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private void submitRequest() {
        String title = titleField.getText().trim();
        String description = descriptionArea.getText().trim();
        String category = (String) categoryBox.getSelectedItem();
        String location = locationField.getText().trim();
        String contact = contactField.getText().trim();

        if (title.isEmpty()
                || description.isEmpty()
                || location.isEmpty()
                || contact.isEmpty()) {

            showError("Please complete every field.");
            return;
        }

        if (title.length() > 120
                || description.length() > 3000
                || location.length() > 150
                || contact.length() > 200) {

            showError("One or more fields exceed the allowed length.");
            return;
        }

        String json = "{"
                + "\"title\":" + jsonString(title) + ","
                + "\"description\":" + jsonString(description) + ","
                + "\"category\":" + jsonString(category) + ","
                + "\"location\":" + jsonString(location) + ","
                + "\"contact\":" + jsonString(contact)
                + "}";

        sendRequest("POST", "/requests", json, response -> {
            if (response.statusCode() == 201) {
                clearForm();
                statusLabel.setText("Request created successfully.");
                refreshRequests();
            } else {
                showApiError(response.body());
            }
        });
    }

    private void refreshRequests() {
        String category = (String) filterBox.getSelectedItem();
        String search = searchField.getText().trim();

        StringBuilder path = new StringBuilder("/requests");

        List<String> parameters = new ArrayList<>();

        if (category != null && !category.equals("All categories")) {
            parameters.add("category=" + urlEncode(category));
        }

        if (!search.isEmpty()) {
            parameters.add("search=" + urlEncode(search));
        }

        if (!parameters.isEmpty()) {
            path.append("?").append(String.join("&", parameters));
        }

        sendRequest("GET", path.toString(), null, response -> {
            if (response.statusCode() != 200) {
                showApiError(response.body());
                return;
            }

            List<HelpRequest> requests;

            try {
                requests = parseRequests(response.body());
            } catch (RuntimeException error) {
                showError("The server returned invalid request data.");
                return;
            }

            listModel.clear();

            for (HelpRequest request : requests) {
                listModel.addElement(request);
            }

            detailsArea.setText("");

            statusLabel.setText(
                    requests.size() + " request(s) found."
            );
        });
    }

    private void updateSelectedStatus(String status) {
        HelpRequest selected = requestList.getSelectedValue();

        if (selected == null) {
            showError("Please select a request first.");
            return;
        }

        String json = "{\"status\":" + jsonString(status) + "}";

        sendRequest(
                "PATCH",
                "/requests/" + selected.id,
                json,
                response -> {
                    if (response.statusCode() == 200) {
                        statusLabel.setText("Request status updated.");
                        refreshRequests();
                    } else {
                        showApiError(response.body());
                    }
                }
        );
    }

    private void showSelectedRequest() {
        HelpRequest request = requestList.getSelectedValue();

        if (request == null) {
            detailsArea.setText("");
            return;
        }

        detailsArea.setText(
                "Request #" + request.id
                        + "\n\nTitle: " + request.title
                        + "\nCategory: " + request.category
                        + "\nLocation: " + request.location
                        + "\nStatus: " + request.status
                        + "\nCreated: " + request.createdAt
                        + "\n\nDescription:\n" + request.description
                        + "\n\nContact: " + request.contact
        );

        detailsArea.setCaretPosition(0);
    }

    private void clearForm() {
        titleField.setText("");
        descriptionArea.setText("");
        locationField.setText("");
        contactField.setText("");
        categoryBox.setSelectedIndex(0);
    }

    private void sendRequest(
            String method,
            String path,
            String body,
            java.util.function.Consumer<HttpResponse<String>> callback
    ) {
        statusLabel.setText("Communicating with the server...");

        new SwingWorker<HttpResponse<String>, Void>() {
            @Override
            protected HttpResponse<String> doInBackground()
                    throws Exception {

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(API + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json");

                if (body != null) {
                    builder.header(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    );

                    builder.method(
                            method,
                            HttpRequest.BodyPublishers.ofString(
                                    body,
                                    StandardCharsets.UTF_8
                            )
                    );
                } else {
                    builder.method(
                            method,
                            HttpRequest.BodyPublishers.noBody()
                    );
                }

                return httpClient.send(
                        builder.build(),
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                );
            }

            @Override
            protected void done() {
                try {
                    callback.accept(get());
                } catch (Exception error) {
                    statusLabel.setText("Unable to reach the Python API.");

                    JOptionPane.showMessageDialog(
                            CommunityHelpHub.this,
                            "Make sure the Python backend is running at "
                                    + API + ".\n\nDetails: "
                                    + error.getMessage(),
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private List<HelpRequest> parseRequests(String json) {
        String array = extractValue(json, "requests");

        List<HelpRequest> requests = new ArrayList<>();

        if (array == null || array.equals("null")) {
            return requests;
        }

        Matcher matcher = Pattern.compile(
                "\\{([^{}]*)\\}"
        ).matcher(array);

        while (matcher.find()) {
            String object = matcher.group();

            requests.add(new HelpRequest(
                    Integer.parseInt(requireValue(object, "id")),
                    requireValue(object, "title"),
                    requireValue(object, "description"),
                    requireValue(object, "category"),
                    requireValue(object, "location"),
                    requireValue(object, "contact"),
                    requireValue(object, "status"),
                    requireValue(object, "created_at")
            ));
        }

        return requests;
    }

    private String requireValue(String json, String key) {
        String value = extractValue(json, key);

        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing JSON field: " + key
            );
        }

        return value;
    }

    /*
     * Small JSON reader for the fixed response format returned by this API.
     * For a larger application, use a dedicated JSON library.
     */
    private String extractValue(String json, String key) {
        String prefix = "\"" + key + "\"";

        int keyPosition = json.indexOf(prefix);

        if (keyPosition < 0) {
            return null;
        }

        int colon = json.indexOf(':', keyPosition + prefix.length());

        if (colon < 0) {
            return null;
        }

        int position = colon + 1;

        while (position < json.length()
                && Character.isWhitespace(json.charAt(position))) {
            position++;
        }

        if (position >= json.length()) {
            return null;
        }

        if (json.charAt(position) == '"') {
            StringBuilder value = new StringBuilder();
            position++;

            while (position < json.length()) {
                char character = json.charAt(position++);

                if (character == '"') {
                    return value.toString();
                }

                if (character == '\\') {
                    if (position >= json.length()) {
                        throw new IllegalArgumentException(
                                "Invalid JSON escape."
                        );
                    }

                    char escaped = json.charAt(position++);

                    switch (escaped) {
                        case '"':
                            value.append('"');
                            break;
                        case '\\':
                            value.append('\\');
                            break;
                        case '/':
                            value.append('/');
                            break;
                        case 'b':
                            value.append('\b');
                            break;
                        case 'f':
                            value.append('\f');
                            break;
                        case 'n':
                            value.append('\n');
                            break;
                        case 'r':
                            value.append('\r');
                            break;
                        case 't':
                            value.append('\t');
                            break;
                        case 'u':
                            if (position + 4 > json.length()) {
                                throw new IllegalArgumentException(
                                        "Invalid Unicode escape."
                                );
                            }

                            String hex = json.substring(
                                    position,
                                    position + 4
                            );

                            value.append((char) Integer.parseInt(hex, 16));
                            position += 4;
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Invalid JSON escape."
                            );
                    }
                } else {
                    value.append(character);
                }
            }

            throw new IllegalArgumentException(
                    "Unterminated JSON string."
            );
        }

        int end = position;

        while (end < json.length()
                && json.charAt(end) != ','
                && json.charAt(end) != '}'
                && json.charAt(end) != ']') {
            end++;
        }

        return json.substring(position, end).trim();
    }

    private String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            switch (character) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        result.append(
                                String.format("\\u%04x", (int) character)
                        );
                    } else {
                        result.append(character);
                    }
            }
        }

        return result.append('"').toString();
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void showApiError(String responseBody) {
        String message = extractValue(responseBody, "error");

        if (message == null || message.isBlank()) {
            message = "The server could not complete the operation.";
        }

        showError(message);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Community Help Hub",
                JOptionPane.WARNING_MESSAGE
        );
    }

    private static class HelpRequest {
        final int id;
        final String title;
        final String description;
        final String category;
        final String location;
        final String contact;
        final String status;
        final String createdAt;

        HelpRequest(
                int id,
                String title,
                String description,
                String category,
                String location,
                String contact,
                String status,
                String createdAt
        ) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.category = category;
            this.location = location;
            this.contact = contact;
            this.status = status;
            this.createdAt = createdAt;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName()
                );
            } catch (Exception ignored) {
                // Use Java's default appearance if unavailable.
            }

            new CommunityHelpHub().setVisible(true);
        });
    }
}