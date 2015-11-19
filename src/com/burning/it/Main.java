package com.burning.it;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static String inputFileName;
    private static String outputFileName;
    private static int entriesMigrated = 0;
    private static int entriesSkipped = 0;

    // Inbank CSV number of fields
    public static final int InbankFieldsNum = 8;

    // Inbank CSV field mapping
    public class InbankFields {
        public static final int DATACONTABILE = 0;
        public static final int DATAVALUTA = 1;
        public static final int DARE = 2;
        public static final int AVERE = 3;
        public static final int VALUTA = 4;
        public static final int DESCRIZIONE = 5;
        public static final int CAUSALE = 6;
    }

    // Homebank CSV number of fields
    public static final int HomebankFieldsNum = 8;

    // Defaults
    public static final String DefaultPaymode = "0";
    public static final String DefaultInfo = "";
    public static final String DefaultPayee = "";
    public static final String DefaultCategory = "";
    public static final String DefaultTag = "generic";

    // Homebank CSV field mapping
    public class HomebankFields {
        public static final int DATE = 0;
        public static final int PAYMODE = 1;
        public static final int INFO = 2;
        public static final int PAYEE = 3;
        public static final int MEMO = 4;
        public static final int AMOUNT = 5;
        public static final int CATEGORY = 6;
        public static final int TAGS = 7;
    }

    // Ouput buffer
    private static List<String[]> outputBuffer = new ArrayList<String[]>();

    public static void main(String[] args) {
        // Manage arguments
        switch (args.length) {
            case 0:
                System.out.println("Usage: inbank2homebank [path/to/input.csv] [optional: path/to/output.csv]");
                System.exit(0);
                break;
            case 1:
                inputFileName = args[0];
                outputFileName = inputFileName + ".out";
                break;
            case 2:
                inputFileName = args[0];
                outputFileName = args[1];
                break;
            default:
                System.out.println("Too many parameters");
                System.exit(0);
        }

        // Process files
        if (!inputFileName.isEmpty() && !outputFileName.isEmpty()) {
            processCsv();
        } else {
            System.out.println("Input or Output file path/names are invalid");
            System.exit(0);
        }
    }

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
        // TODO: Skip N lines at the beginning and N lines at the end: change iteration on csvEntries with standard for iterator over array
        if (csvEntries != null) {
            for (String[] entry: csvEntries) {
                // Skip this row if it doesn't match the column count we expect
                if (entry.length == InbankFieldsNum) {
                    String[] newRow = new String[HomebankFieldsNum];

                    ////////////////////////////////////////////////////////////////////////////////
                    // Create the new row
                    newRow[HomebankFields.DATE] = entry[InbankFields.DATAVALUTA];
                    newRow[HomebankFields.PAYMODE] = DefaultPaymode;
                    newRow[HomebankFields.INFO] = DefaultInfo;
                    newRow[HomebankFields.PAYEE] = DefaultPayee;
                    newRow[HomebankFields.MEMO] = entry[InbankFields.DESCRIZIONE];

                    if (!entry[InbankFields.DARE].isEmpty()) {
                        newRow[HomebankFields.AMOUNT] = "-" + entry[InbankFields.DARE];
                    } else {
                        newRow[HomebankFields.AMOUNT] = entry[InbankFields.AVERE];
                    }

                    newRow[HomebankFields.CATEGORY] = DefaultCategory;
                    newRow[HomebankFields.TAGS] = DefaultTag;
                    // New row END
                    ////////////////////////////////////////////////////////////////////////////////

                    if (newRow != null) {
                        outputBuffer.add(newRow);
                    } else {
                        System.out.println("Nothing to write");
                    }

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

    private static void saveOutput() {
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new FileWriter(outputFileName), ';', CSVWriter.NO_QUOTE_CHARACTER);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // feed in your array (or convert your data to an array)
        for (String[] entry: outputBuffer) {
            writer.writeNext(entry);
        }

        // Close
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
