/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.markdown;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.utils.Base64ImageUtils;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.*;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownGenerator implements Closeable {

    protected static final Logger LOGGER = Logger.getLogger(MarkdownGenerator.class.getCanonicalName());
    protected final FileWriter markdownWriter;
    protected final String markdownFileName;
    protected int tableNesting = 0;
    protected boolean isImageSupported;
    protected String markdownPageSeparator;
    protected boolean embedImages = false;
    protected String imageFormat = Config.IMAGE_FORMAT_PNG;
    protected boolean includeHeaderFooter = false;
    protected boolean inContentsSection = false;

    private static final Pattern CONTENTS_HEADING_PATTERN = Pattern.compile("^(contents|table of contents)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENTS_ENTRY_MARKER_PATTERN = Pattern.compile(
        "(?:"
            + "Cover"
            + "|Title\\s+Page"
            + "|(?:Table\\s+of\\s+)?Contents"
            + "|About\\s+(?:the|this)\\s+(?:Book|Author)"
            + "|(?:Also|Other\\s+Books)\\s+by\\s+.+?"
            + "|Foreword(?:\\s+by\\s+.+?)?"
            + "|Preface"
            + "|Prologue"
            + "|Introduction"
            + "|Afterword"
            + "|Epilogue"
            + "|Acknowledg(?:e)?ments?"
            + "|Notes"
            + "|References"
            + "|Bibliography"
            + "|Index"
            + "|Picture\\s+Credits"
            + "|Copyright"
            + "|Appendix(?:\\s+[A-Z0-9IVXLC]+)?(?:\\s*:\\s+[^\\d].*?)?"
            + "|Part\\s+(?:[0-9]+|[IVXLC]+|[A-Z]+)(?:\\s*:\\s+.*?|\\s+-\\s+.*?)?"
            + "|Chapter\\s+(?:[0-9]+|[IVXLC]+|[A-Z]+)(?:\\s*:\\s+.*?|\\s+-\\s+.*?)?"
            + ")(?=\\s+(?:"
            + "Cover"
            + "|Title\\s+Page"
            + "|(?:Table\\s+of\\s+)?Contents"
            + "|About\\s+(?:the|this)\\s+(?:Book|Author)"
            + "|(?:Also|Other\\s+Books)\\s+by\\s+[A-Z]"
            + "|Foreword(?:\\s+by\\s+[A-Z])?"
            + "|Preface"
            + "|Prologue"
            + "|Introduction"
            + "|Afterword"
            + "|Epilogue"
            + "|Acknowledg(?:e)?ments?"
            + "|Notes"
            + "|References"
            + "|Bibliography"
            + "|Index"
            + "|Picture\\s+Credits"
            + "|Copyright"
            + "|Appendix(?:\\s+[A-Z0-9IVXLC]+)?"
            + "|Part\\s+(?:[0-9]+|[IVXLC]+|[A-Z]+)"
            + "|Chapter\\s+(?:[0-9]+|[IVXLC]+|[A-Z]+)"
            + ")|$)",
        Pattern.CASE_INSENSITIVE);

    MarkdownGenerator(File inputPdf, Config config) throws IOException {
        String cutPdfFileName = inputPdf.getName();
        this.markdownFileName = config.getOutputFolder() + File.separator + cutPdfFileName.substring(0, cutPdfFileName.length() - 3) + "md";
        this.markdownWriter = new FileWriter(markdownFileName, StandardCharsets.UTF_8);
        this.isImageSupported = !config.isImageOutputOff() && config.isGenerateMarkdown();
        this.markdownPageSeparator = config.getMarkdownPageSeparator();
        this.embedImages = config.isEmbedImages();
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
    }

    public void writeToMarkdown(List<List<IObject>> contents) {
        try {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                writePageSeparator(pageNumber);
                for (IObject content : contents.get(pageNumber)) {
                    if (!isSupportedContent(content)) {
                        continue;
                    }
                    this.write(content);
                    writeContentsSeparator();
                }
            }

            LOGGER.log(Level.INFO, "Created {0}", markdownFileName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to create markdown output: " + e.getMessage());
        }
    }

    protected void writePageSeparator(int pageNumber) throws IOException {
        if (!markdownPageSeparator.isEmpty()) {
            markdownWriter.write(markdownPageSeparator.contains(Config.PAGE_NUMBER_STRING)
                ? markdownPageSeparator.replace(Config.PAGE_NUMBER_STRING, String.valueOf(pageNumber + 1))
                : markdownPageSeparator);
            writeContentsSeparator();
        }
    }

    protected boolean isSupportedContent(IObject content) {
        if (content instanceof SemanticHeaderOrFooter) {
            return includeHeaderFooter;
        }
        return content instanceof SemanticTextNode || // Heading, Paragraph etc...
            content instanceof SemanticFormula ||
            content instanceof SemanticPicture ||
            content instanceof TableBorder ||
            content instanceof PDFList ||
            (content instanceof ImageChunk && isImageSupported);
    }

    protected void writeContentsSeparator() throws IOException {
        writeLineBreak();
        writeLineBreak();
    }

    protected void write(IObject object) throws IOException {
        if (object instanceof SemanticHeaderOrFooter) {
            writeHeaderOrFooter((SemanticHeaderOrFooter) object);
        } else if (object instanceof SemanticPicture) {
            writePicture((SemanticPicture) object);
        } else if (object instanceof ImageChunk) {
            writeImage((ImageChunk) object);
        } else if (object instanceof SemanticFormula) {
            writeFormula((SemanticFormula) object);
        } else if (object instanceof SemanticHeading) {
            writeHeading((SemanticHeading) object);
        } else if (object instanceof SemanticParagraph) {
            writeParagraph((SemanticParagraph) object);
        } else if (object instanceof SemanticTextNode) {
            writeSemanticTextNode((SemanticTextNode) object);
        } else if (object instanceof TableBorder) {
            writeTable((TableBorder) object);
        } else if (object instanceof PDFList) {
            writeList((PDFList) object);
        }
    }

    protected void writeImage(ImageChunk image) {
        try {
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, image.getIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", image.getIndex(), imageFormat);

            if (ImagesUtils.isImageFileExists(absolutePath)) {
                String imageSource;
                if (embedImages) {
                    File imageFile = new File(absolutePath);
                    imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                    if (imageSource == null) {
                        LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                    }
                } else {
                    imageSource = relativePath;
                }
                if (imageSource != null) {
                    String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, "image " + image.getIndex(), imageSource);
                    markdownWriter.write(getCorrectMarkdownString(imageString));
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write image for markdown output: " + e.getMessage());
        }
    }

    /**
     * Writes a SemanticPicture with its description as alt text.
     *
     * @param picture The picture to write
     */
    protected void writePicture(SemanticPicture picture) {
        try {
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, picture.getPictureIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", picture.getPictureIndex(), imageFormat);

            if (ImagesUtils.isImageFileExists(absolutePath)) {
                String imageSource;
                if (embedImages) {
                    File imageFile = new File(absolutePath);
                    imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                    if (imageSource == null) {
                        LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                    }
                } else {
                    imageSource = relativePath;
                }
                if (imageSource != null) {
                    // Use simple alt text
                    String altText = "image " + picture.getPictureIndex();
                    String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, altText, imageSource);
                    markdownWriter.write(getCorrectMarkdownString(imageString));

                    // Add caption as italic text below the image if description available
                    if (picture.hasDescription()) {
                        markdownWriter.write(MarkdownSyntax.DOUBLE_LINE_BREAK);
                        String caption = picture.getDescription().replace("\n", " ").replace("\r", "");
                        markdownWriter.write("*" + getCorrectMarkdownString(caption) + "*");
                        markdownWriter.write(MarkdownSyntax.DOUBLE_LINE_BREAK);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write picture for markdown output: " + e.getMessage());
        }
    }

    /**
     * Writes a formula in LaTeX format wrapped in $$ delimiters.
     *
     * @param formula The formula to write
     */
    protected void writeFormula(SemanticFormula formula) throws IOException {
        markdownWriter.write(MarkdownSyntax.MATH_BLOCK_START);
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        markdownWriter.write(formula.getLatex());
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        markdownWriter.write(MarkdownSyntax.MATH_BLOCK_END);
    }

    protected void writeHeaderOrFooter(SemanticHeaderOrFooter headerOrFooter) throws IOException {
        for (IObject content : headerOrFooter.getContents()) {
            if (isSupportedContent(content)) {
                write(content);
                writeContentsSeparator();
            }
        }
    }

    protected void writeList(PDFList list) throws IOException {
        for (ListItem item : list.getListItems()) {
            if (!isInsideTable()) {
                markdownWriter.write(MarkdownSyntax.LIST_ITEM);
                markdownWriter.write(MarkdownSyntax.SPACE);
            }
            markdownWriter.write(getCorrectMarkdownString(item.toString()));
            writeLineBreak();

            List<IObject> itemContents = item.getContents();
            if (!itemContents.isEmpty()) {
                writeLineBreak();
                writeContents(itemContents, false);
            }
        }
    }

    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        String value = textNode.getValue();
        if (StaticContainers.isKeepLineBreaks()) {
            if (textNode instanceof SemanticHeading) {
                value = value.replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
            } else if (isInsideTable()) {
                value = value.replace(MarkdownSyntax.LINE_BREAK, getLineBreak());
            }
        } else if (isInsideTable()) {
            // Always replace line breaks with space in table cells for proper markdown table formatting
            value = value.replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
        }

        markdownWriter.write(getCorrectMarkdownString(value));
    }

    protected void writeTable(TableBorder table) throws IOException {
        enterTable();
        for (TableBorderRow row : table.getRows()) {
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (TableBorderCell cell : row.getCells()) {
                List<IObject> cellContents = cell.getContents();
                writeContents(cellContents, true);
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            }
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            //Due to markdown syntax we have to separate column headers
            if (row.getRowNumber() == 0) {
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                for (int i = 0; i < table.getNumberOfColumns(); i++) {
                    markdownWriter.write(MarkdownSyntax.TABLE_HEADER_SEPARATOR);
                    markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                }
                markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            }
        }
        leaveTable();
    }

    protected void writeContents(List<IObject> contents, boolean isTable) throws IOException {
        boolean wroteAnyContent = false;
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (!isSupportedContent(content)) {
                continue;
            }
            this.write(content);
            boolean isLastContent = i == contents.size() - 1;
            if (!isTable || !isLastContent) {
                writeContentsSeparator();
            }
            wroteAnyContent = true;
        }
        if (!wroteAnyContent && isTable) {
            writeSpace();
        }
    }

    protected void writeParagraph(SemanticParagraph textNode) throws IOException {
        if (inContentsSection) {
            List<String> entries = splitFlattenedContentsEntries(textNode.getValue());
            if (!entries.isEmpty()) {
                for (int i = 0; i < entries.size(); i++) {
                    markdownWriter.write(getCorrectMarkdownString(entries.get(i)));
                    if (i < entries.size() - 1) {
                        writeLineBreak();
                    }
                }
                return;
            }
        }
        writeSemanticTextNode(textNode);
    }

    protected void writeHeading(SemanticHeading heading) throws IOException {
        String headingText = heading.getValue() == null ? "" : heading.getValue().replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE).trim();
        inContentsSection = CONTENTS_HEADING_PATTERN.matcher(headingText).matches();
        if (!isInsideTable()) {
            // Cap heading level to 1-6 per Markdown specification
            int headingLevel = Math.min(6, Math.max(1, heading.getHeadingLevel()));
            for (int i = 0; i < headingLevel; i++) {
                markdownWriter.write(MarkdownSyntax.HEADING_LEVEL);
            }
            markdownWriter.write(MarkdownSyntax.SPACE);
        }
        writeSemanticTextNode(heading);
    }

    protected void enterTable() {
        tableNesting++;
    }

    protected void leaveTable() {
        if (tableNesting > 0) {
            tableNesting--;
        }
    }

    protected boolean isInsideTable() {
        return tableNesting > 0;
    }

    protected String getLineBreak() {
        if (isInsideTable()) {
            return MarkdownSyntax.HTML_LINE_BREAK_TAG;
        } else {
            return MarkdownSyntax.LINE_BREAK;
        }
    }

    protected void writeLineBreak() throws IOException {
        markdownWriter.write(getLineBreak());
    }

    protected void writeSpace() throws IOException {
        markdownWriter.write(MarkdownSyntax.SPACE);
    }

    protected String getCorrectMarkdownString(String value) {
        if (value != null) {
            return value.replace("\u0000", " ");
        }
        return null;
    }

    static List<String> splitFlattenedContentsEntries(String value) {
        List<String> entries = new ArrayList<>();
        if (value == null) {
            return entries;
        }

        String normalized = value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace('\t', ' ')
            .replaceAll("\\s+", " ")
            .trim();
        if (normalized.isEmpty()) {
            return entries;
        }

        Matcher matcher = CONTENTS_ENTRY_MARKER_PATTERN.matcher(normalized);
        List<int[]> spans = new ArrayList<>();
        while (matcher.find()) {
            spans.add(new int[]{matcher.start(), matcher.end()});
        }
        if (spans.size() <= 1) {
            return entries;
        }

        for (int i = 0; i < spans.size(); i++) {
            int start = spans.get(i)[0];
            int end = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : normalized.length();
            String chunk = normalized.substring(start, end).trim().replaceFirst("^[\\-–—•]+\\s*", "");
            if (!chunk.isEmpty()) {
                entries.add(chunk);
            }
        }
        return entries;
    }

    @Override
    public void close() throws IOException {
        if (markdownWriter != null) {
            markdownWriter.close();
        }
    }
}
