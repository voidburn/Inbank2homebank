package com.burning.it.i2h;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.apache.commons.cli.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    // Settings
    public static final int InbankFieldsNum = 8;
    public static final int HomebankFieldsNum = 8;
    public static final int skipAtStart = 2;
    public static final int skipAtEnd = 3;

    // Homebank import default values if null in Inbank
    public static final String DefaultPaymode = "0";
    public static final String DefaultInfo = "";
    public static final String DefaultPayee = "";
    public static final String DefaultCategory = "";
    public static final String DefaultTag = "generic";

    // Tracking fields
    private static String inputFileName;
    private static String outputFileName;
    private static int entriesMigrated = 0;
    private static int entriesSkipped = 0;

    // Output buffer
    private static List<String[]> outputBuffer = new ArrayList<String[]>();

    // Inbank CSV field mapping (filed name => cell index in row)
    public static class InbankFields {
        public static final int DATAVALUTA = 1;     // Transaction date
        public static final int DARE = 2;           // Expense
        public static final int AVERE = 3;          // Income
        public static final int DESCRIZIONE = 5;    // Description
    }

    // Homebank CSV field mapping (filed name => cell index in row)
    public static class HomebankFields {
        public static final int DATE = 0;           // Transaction date
        public static final int PAYMODE = 1;        // Paymode
        public static final int INFO = 2;           // Info
        public static final int PAYEE = 3;          // Payee
        public static final int MEMO = 4;           // Description
        public static final int AMOUNT = 5;         // Normalized amount
        public static final int CATEGORY = 6;       // Category
        public static final int TAGS = 7;           // Tags
    }

    // Command line management
    private static CommandLine cmd = null;
    private static final Options options = new Options();
    private static final CommandLineParser parser = new DefaultParser();
    private static final HelpFormatter formatter = new HelpFormatter();


    /**
     * Entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        // Command line options
        options.addOption("in", true, "Input file");
        options.addOption("out", true, "Output file (optional, defaults to 'inputfilename.out.csv')");

        // Parse command line
        try {
            // parse the command line arguments
            cmd = parser.parse(options, args);
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
        }

        // Process the input file command line
        if (cmd.hasOption("in")) {
            // Input file
            inputFileName = cmd.getOptionValue("in");

            // Output file
            if (cmd.hasOption("out")) {
                outputFileName = cmd.getOptionValue("out");
            } else {
                outputFileName = inputFileName.substring(0, inputFileName.length() - 4) + ".out.csv";
            }

            // Process files
            if (!inputFileName.isEmpty() && !outputFileName.isEmpty()) {
                processCsv();
            } else {
                System.out.println("Input or Output file path/names are invalid");
                System.exit(0);
            }
        } else {
            printHelp();
        }
    }

    /**
     * Migration processing
     */
    private static void processCsv() {
        System.out.println("Input filename: " + inputFileName);
        System.out.println("Output filename: " + outputFileName);

        // Read the input file
        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(inputFileName), ';');
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Try to parse the input file
        List<String[]> csvEntries = null;
        if (reader != null) {
            try {
                csvEntries = reader.readAll();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // We have data, let's process it
        if (csvEntries != null) {
            int start = skipAtStart;
            int end = csvEntries.size() - skipAtEnd;

            System.out.println("Starting at row: " + start + ", skipping " + skipAtStart + " line(s) at the start");
            System.out.println("Ending at row: " + end + ", skipping " + skipAtEnd + " line(s) at the end");

            for (int i = start; i < end; i++) {
                // Fetch row
                String[] row = csvEntries.get(i);

                // Skip this row if it doesn't match the column count we expect
                if (row.length == InbankFieldsNum) {
                    String[] newRow = new String[HomebankFieldsNum];

                    ////////////////////////////////////////////////////////////////////////////////
                    // Create the new row
                    newRow[HomebankFields.DATE] = row[InbankFields.DATAVALUTA];
                    newRow[HomebankFields.PAYMODE] = DefaultPaymode;
                    newRow[HomebankFields.INFO] = DefaultInfo;
                    newRow[HomebankFields.PAYEE] = DefaultPayee;
                    newRow[HomebankFields.MEMO] = row[InbankFields.DESCRIZIONE];

                    if (!row[InbankFields.DARE].isEmpty()) {
                        newRow[HomebankFields.AMOUNT] = "-" + row[InbankFields.DARE];
                    } else {
                        newRow[HomebankFields.AMOUNT] = row[InbankFields.AVERE];
                    }

                    newRow[HomebankFields.CATEGORY] = DefaultCategory;
                    newRow[HomebankFields.TAGS] = DefaultTag;
                    // New row END
                    ////////////////////////////////////////////////////////////////////////////////

                    // Append new row to the output buffer
                    outputBuffer.add(newRow);

                    // Next line
                    entriesMigrated++;
                } else {
                    entriesSkipped++;
                }
            }

            // Save output file
            saveOutput();

            // Report
            System.out.println("Migrated rows: " + entriesMigrated);
            System.out.println("Skipped non matching rows: " + entriesSkipped);
        } else {
            System.out.println("Could not parse csv file");
            System.exit(0);
        }
    }

    /**
     * Save processed buffer to output file
     */
    private static void saveOutput() {
        // Open a handle to the output file
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new FileWriter(outputFileName), ';', CSVWriter.NO_QUOTE_CHARACTER);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Write all rows to the file
        if (writer != null) {
            for (String[] entry : outputBuffer) {
                writer.writeNext(entry);
            }

            // Close the file handle
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Print command line help
     */
    private static void printHelp() {
        formatter.printHelp("inbank2homebank", options);
    }
}
