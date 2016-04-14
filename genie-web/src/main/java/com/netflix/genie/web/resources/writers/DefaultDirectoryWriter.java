/*
 *
 *  Copyright 2016 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie.web.resources.writers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.Data;
import org.apache.catalina.util.ConcurrentDateFormat;
import org.apache.catalina.util.ServerInfo;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * A default directory writer implementation.
 *
 * @author tgianos
 * @since 3.0.0
 */
public class DefaultDirectoryWriter implements DirectoryWriter {

    private static final String DEFAULT_CSS =
        "H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} "
            + "H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} "
            + "H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} "
            + "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} "
            + "B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} "
            + "P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}"
            + "A {color : black;}"
            + "A.name {color : black;}"
            + ".line {height: 1px; background-color: #525D76; border: none;}";

    /**
     * {@inheritDoc}
     *
     * @see org.apache.catalina.servlets.DefaultServlet
     */
    @Override
    public String toHtml(
        @NotNull final File directory,
        @URL final String requestURL,
        final boolean includeParent
    ) throws IOException {
        final Directory dir = this.getDirectory(directory, requestURL, includeParent);

        final StringBuilder builder = new StringBuilder();

        // Render the page header
        builder.append("<!DOCTYPE html>");
        builder.append("<html>");
        builder.append("<head>");
        builder.append("<title>");
        builder.append(directory.getName());
        builder.append("</title>");
        builder.append("<style type=\"text/css\"><!--");
        builder.append(DEFAULT_CSS);
        builder.append("--></style> ");
        builder.append("</head>");

        // Body
        builder.append("<body>");
        builder.append("<h1>").append(directory.getName()).append("</h1>");

        builder.append("<HR size=\"1\" noshade=\"noshade\">");

        builder.append("<table width=\"100%\" cellspacing=\"0\"" + " cellpadding=\"5\" align=\"center\">");

        // Render the column headings
        builder.append("<tr>");
        builder.append("<td align=\"left\"><font size=\"+1\"><strong>");
        builder.append("Filename");
        builder.append("</strong></font></td>");
        builder.append("<td align=\"right\"><font size=\"+1\"><strong>");
        builder.append("Size");
        builder.append("</strong></font></td>");
        builder.append("<td align=\"right\"><font size=\"+1\"><strong>");
        builder.append("Last Modified");
        builder.append("</strong></font></td>");
        builder.append("</tr>");

        // Write parent if necessary
        if (dir.getParent() != null) {
            this.writeFileHtml(builder, false, dir.getParent());
        }

        boolean shade = true;

        // Write directories
        if (dir.getDirectories() != null) {
            for (final Entry entry : dir.getDirectories()) {
                this.writeFileHtml(builder, shade, entry);
                shade = !shade;
            }
        }

        // Write files
        if (dir.getFiles() != null) {
            for (final Entry entry : dir.getFiles()) {
                this.writeFileHtml(builder, shade, entry);
                shade = !shade;
            }
        }

        // Render the page footer
        builder.append("</table>");

        builder.append("<HR size=\"1\" noshade=\"noshade\">");
        // TODO: replace with something related to Genie
        builder.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        builder.append("</body>");
        builder.append("</html>");

        return builder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toJson(
        @NotNull final File directory,
        @URL final String requestURL,
        final boolean includeParent
    ) throws Exception {
        final Directory dir = this.getDirectory(directory, requestURL, includeParent);
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(dir);
    }

    private String renderSize(final long size) {
        final long kb = 1024;
        final long leftSide = size / kb;
        long rightSide = (size % kb) / 103;   // Makes 1 digit
        if (leftSide == 0 && rightSide == 0 && size > 0) {
            rightSide = 1;
        }

        return leftSide + "." + rightSide + " kb";
    }

    private void writeFileHtml(
        final StringBuilder builder,
        final boolean shade,
        final Entry entry
    ) {
        builder.append("<tr");
        if (shade) {
            builder.append(" bgcolor=\"#eeeeee\"");
        }
        builder.append(">");

        builder.append("<td align=\"left\">&nbsp;&nbsp;");
        builder.append("<a href=\"").append(entry.getUrl()).append("\">");
        builder.append("<tt>").append(entry.getName()).append("</tt></a></td>");
        builder
            .append("<td align=\"right\"><tt>")
            .append(this.renderSize(entry.getSize())).append("</tt></td>");
        final String lastModified = ConcurrentDateFormat.formatRfc1123(new Date(entry.getLastModified()));
        builder.append("<td align=\"right\"><tt>").append(lastModified).append("</tt></td>");
        builder.append("</tr>");
    }

    protected Directory getDirectory(final File directory, final String requestUrl, final boolean includeParent) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Input directory is not a valid directory. Unable to continue.");
        }
        if (StringUtils.isBlank(requestUrl)) {
            throw new IllegalArgumentException("No request url entered. Unable to continue.");
        }
        final Directory dir = new Directory();

        if (includeParent) {
            final Entry parent = new Entry();
            String url = requestUrl;
            if (url.charAt(url.length() - 1) == '/') {
                url = url.substring(0, url.length() - 2);
            }
            // Rip off the last directory
            url = url.substring(0, url.lastIndexOf('/'));
            parent.setName("../");
            parent.setUrl(url);
            parent.setSize(directory.getParentFile().getAbsoluteFile().length());
            parent.setLastModified(directory.getParentFile().getAbsoluteFile().lastModified());
            dir.setParent(parent);
        }

        final File[] files = directory.listFiles();
        dir.setDirectories(Lists.newArrayList());
        dir.setFiles(Lists.newArrayList());
        final String baseURL = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";
        if (files != null) {
            for (final File file : files) {
                final Entry entry = new Entry();
                entry.setSize(file.getAbsoluteFile().length());
                entry.setLastModified(file.getAbsoluteFile().lastModified());
                if (file.isDirectory()) {
                    entry.setName(file.getName() + "/");
                    entry.setUrl(baseURL + file.getName() + "/");
                    dir.getDirectories().add(entry);
                } else {
                    entry.setName(file.getName());
                    entry.setUrl(baseURL + file.getName());
                    dir.getFiles().add(entry);
                }
            }
        }

        dir.getDirectories().sort(
            (final Entry entry1, final Entry entry2) -> entry1.getName().compareTo(entry2.getName())
        );

        dir.getFiles().sort(
            (final Entry entry1, final Entry entry2) -> entry1.getName().compareTo(entry2.getName())
        );

        return dir;
    }

    @Data
    protected static class Directory {
        private Entry parent;
        private List<Entry> directories;
        private List<Entry> files;
    }

    @Data
    protected static class Entry {
        @NotBlank
        private String name;
        @URL
        private String url;
        @Size(min = 0)
        private long size;
        @Size(min = 0)
        private long lastModified;
    }
}
