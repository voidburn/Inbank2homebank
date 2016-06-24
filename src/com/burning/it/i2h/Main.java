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
    // Supported banks
    private enum BANK {
        INBANK,
        MEDIOLANUM
    }

    // Settings
    private static final int HomebankFieldsNum = 8;
    private static int inbankFieldsNum;
    private static int mediolanumFieldsNum;
    private static int skipAtStart;
    private static int skipAtEnd;
    private static BANK selectedBank;

    // Homebank import default values if null in Inbank
    private static final String DefaultPaymode = "0";
    private static final String DefaultInfo = "";
    private static final String DefaultPayee = "";
    private static final String DefaultCategory = "";
    private static final String DefaultTag = "generic";

    // Tracking fields
    private static String inputFileName;
    private static String outputFileName;
    private static int entriesMigrated = 0;
    private static int entriesSkipped = 0;

    // Output buffer
    private static List<String[]> outputBuffer = new ArrayList<>();

    // Inbank CSV field mapping (filed name => cell index in row)
    private static class InbankFields {
        private static final int DATAVALUTA = 1;     // Transaction date
        private static final int DARE = 2;           // Expense
        private static final int AVERE = 3;          // Income
        private static final int DESCRIZIONE = 5;    // Description
    }
    
    private static class MediolanumFields {
        private static final int DATAVALUTA = 1;     // Transaction date
        private static final int DARE = 5;           // Expense
        private static final int AVERE = 4;          // Income
        private static final int DESCRIZIONE = 3;    // Description
    }

    // Homebank CSV field mapping (filed name => cell index in row)
    private static class HomebankFields {
        private static final int DATE = 0;           // Transaction date
        private static final int PAYMODE = 1;        // Paymode
        private static final int INFO = 2;           // Info
        private static final int PAYEE = 3;          // Payee
        private static final int MEMO = 4;           // Description
        private static final int AMOUNT = 5;         // Normalized amount
        private static final int CATEGORY = 6;       // Category
        private static final int TAGS = 7;           // Tags
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
        options.addOption("bank", true, "Bank type. Supported types: inbank, mediolanum");
        options.addOption("in", true, "Input file");
        options.addOption("out", true, "Output file (optional, defaults to 'inputfilename.out.csv')");
        options.addOption("skipstart", true, "How many rows to skip at the start of the csv file (default 1)");
        options.addOption("skipend", true, "How many rows to skip at the end of the csv file (default 3)");

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

            if (cmd.hasOption("bank")) {
                try {
                    String bankDataStructure = cmd.getOptionValue("bank");

                    switch(bankDataStructure) {
                        case "inbank":
                            selectBank(BANK.INBANK);
                            break;
                        case "mediolanum":
                            selectBank(BANK.MEDIOLANUM);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown bank type");
                    }
                } catch (Exception e) {
                    System.out.println("[ERROR] unknown bank type");
                    printHelp();
                }
            } else {
                System.out.println("[WARNING] No bank specified, defaulting to INBANK");
                selectBank(BANK.INBANK);
            }

            // Skip start
            if (cmd.hasOption("skipstart")) {
                try {
                    skipAtStart = Integer.parseInt(cmd.getOptionValue("skipstart"));
                } catch (NumberFormatException e) {
                    System.out.println("[ERROR] skipstart argument does not contain a valid integer number");
                    printHelp();
                }
            }

            // Skip end
            if (cmd.hasOption("skipend")) {
                try {
                    skipAtEnd = Integer.parseInt(cmd.getOptionValue("skipend"));
                } catch (NumberFormatException e) {
                    System.out.println("[ERROR] skipend argument does not contain a valid integer number");
                    printHelp();
                }
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
     * Set defaults in order to work with the specified bank
     *
     * @param type bank type
     */
    private static void selectBank(BANK type) {
        switch (type) {
            case MEDIOLANUM:
                mediolanumFieldsNum = 11;
                skipAtStart = 4;
                skipAtEnd = 0;
                selectedBank = type;
                break;
            case INBANK:
                inbankFieldsNum = 8;
                skipAtStart = 1;
                skipAtEnd = 3;
                selectedBank = type;
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

                ////////////////////////////////////////////////////////////////////////////////
                // Create the new row for INBANK
                if (selectedBank == BANK.INBANK) {
                    if (row.length == inbankFieldsNum) {
                        String[] newRow = new String[HomebankFieldsNum];

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

                        // Append new row to the output buffer
                        outputBuffer.add(newRow);

                        // Next line
                        entriesMigrated++;
                    } else {
                        System.out.println("Skipping non matching row. Expected length: [" + inbankFieldsNum + "], found: " + row.length);
                        entriesSkipped++;
                    }
                }
                // New row END
                ////////////////////////////////////////////////////////////////////////////////

                ////////////////////////////////////////////////////////////////////////////////
                // Create the new row for MEDIOLANUM
                if (selectedBank == BANK.MEDIOLANUM) {
                    if (row.length == mediolanumFieldsNum) {
                        String[] newRow = new String[HomebankFieldsNum];

                        newRow[HomebankFields.DATE] = row[MediolanumFields.DATAVALUTA];
                        newRow[HomebankFields.PAYMODE] = DefaultPaymode;
                        newRow[HomebankFields.INFO] = DefaultInfo;
                        newRow[HomebankFields.PAYEE] = DefaultPayee;
                        newRow[HomebankFields.MEMO] = row[MediolanumFields.DESCRIZIONE];

                        if (!row[MediolanumFields.DARE].isEmpty()) {
                            newRow[HomebankFields.AMOUNT] = "-" + row[MediolanumFields.DARE];
                        } else {
                            newRow[HomebankFields.AMOUNT] = row[MediolanumFields.AVERE];
                        }

                        newRow[HomebankFields.CATEGORY] = DefaultCategory;
                        newRow[HomebankFields.TAGS] = DefaultTag;

                        // Append new row to the output buffer
                        outputBuffer.add(newRow);

                        // Next line
                        entriesMigrated++;
                    } else {
                        System.out.println("Skipping non matching row. Expected length: [" + mediolanumFieldsNum + "], found: " + row.length);
                        entriesSkipped++;
                    }
                }
                // New row END
                ////////////////////////////////////////////////////////////////////////////////
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
