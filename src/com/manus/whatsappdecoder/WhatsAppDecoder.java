/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.manus.whatsappdecoder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringEscapeUtils;

/**
 *
 * @author Manus
 */
public class WhatsAppDecoder {

    public static class WAString {

        public WAString(String string, int line) {
            this.string = unescapeJavaString(string);
            this.line = line;
        }

        String string;
        int line;
    }

    public static class WASourceFile {

        public String file;
        public int line;
        byte[] key = new byte[5];
        boolean decoded = false;
        public List<WAString> input = new ArrayList<WAString>();

        public WASourceFile(String file) {
            this.file = file;
        }

        public void decode() {

            for (WAString original : input) {

                char[] data = original.string.toCharArray();
                char[] output = new char[data.length];
                for (int i = 0; i < data.length; i++) {
                    output[i] = (char) (data[i] ^ key[i % key.length]);
                }
                original.string = new String(output);
            }

            decoded = true;

        }
    }

    public static List<File> recursive(String folder) {
        return recursive(new File(folder));
    }

    public static List<File> recursive(File folder) {
        List<File> res = new ArrayList<>();
        if (folder.isDirectory()) {

            for (String file : folder.list()) {
                File f2 = new File(folder, file);
                if (f2.isDirectory()) {
                    res.addAll(recursive(f2));
                } else {
                    res.add(f2);
                }
            }
        } else {
            res.add(folder);
        }
        return res;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length <= 1) {
            usagre();
        } else {
            String source_folder = "";
            List<File> files = recursive(args[0]);
            System.out.println("Found: " + files.size() + " sources");
            if (args[1].equals("-d")) {
                boolean find = args.length == 3;
                String find_regex = (find)?args[2]:"";
                
                List<WASourceFile> sources = decode_files(files);
                for (WASourceFile source : sources) {
                    if (source.input.size() > 0) {
                        if (source.decoded) {
                            for (WAString wAString : source.input) {
                                if(!find || wAString.string.matches(find_regex))
                                {
                                    System.out.println("'" + wAString.string + "' line " + wAString.line + " file " + source.file);       
                                }
                            }
                        } else {
                            System.out.println("Can't decode: " + source.file);
                        }
                    }
                }
            } else {

                List<WASourceFile> sources = decode_files(files);
                for (WASourceFile source : sources) {
                    for (WAString wAString : source.input) {
                        if (wAString.string.toLowerCase().contains(args[1].toLowerCase())) {
                            System.out.println("'" + wAString.string + "' line " + wAString.line + " file " + source.file);

                        }
                    }
                }
            }

        }
    }

    public static void usagre() {
        System.out.println("Usagre: ");
        System.out.println("<source_dir> <regex> : Search this text in source files");
        System.out.println("<source_dir> -d : Decode all Strings");
        System.out.println("<source_dir> -d <regex> : Decode all Strings and find regex in the decoded strings");
    }

    public static List<WASourceFile> decode_files(List<File> files) {
        List<WASourceFile> ret = new ArrayList<>();
        for (File file : files) {
            ret.add(process_file(file));
        }
        return ret;
    }

    public static List<WAString> search_in_code(File file, String regex) {
        try {
            List<WAString> res = new ArrayList<>();
            BufferedReader in = new BufferedReader(new FileReader(file));
            String line = null;
            int lines = 0;
            while ((line = in.readLine()) != null) {
                lines++;
                if(line.matches(regex))
                {
                    WAString wa = new WAString(line, lines);
                    res.add(wa);
                }
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(WhatsAppDecoder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(WhatsAppDecoder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static WASourceFile process_file(File file) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String line = null;
            boolean key = false;
            boolean key_found = false;
            WASourceFile source = new WASourceFile(file.getAbsolutePath());
            int next_pos = 4;
            int line_count = 0;
            while ((line = in.readLine()) != null) {
                line_count++;
                int i = line.indexOf("\"");
                if (i >= 0) {
                    line = line.substring(i + 1);
                    i = line.indexOf("\".");
                    if (i == -1) {
                        continue;
                    }
                    line = line.substring(0, i);
                    WAString wa = new WAString(line, line_count);
                    source.input.add(wa);
                } else if (!key_found) {
                    if (!key) {

                        i = line.indexOf("% 5");
                        if (i >= 0) {
                            key = true;
                        }
                    } else {
                        i = line.indexOf("= ");
                        if (i >= 0 && line.indexOf("[") == -1) {
                            String num = line.substring(i + 2).replace(";", "");
                            try {
                                source.key[next_pos % 5] = (byte) Integer.parseInt(num);
                                if (next_pos % 5 == 3) {
                                    key_found = true;
                                    next_pos = 4;
                                } else {
                                    next_pos++;
                                }
                            } catch (NumberFormatException ex) {
                                key = false;
                            }
                        }
                    }
                }
            }

            if (key_found) {
                source.decode();
            }
            return source;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(WhatsAppDecoder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(WhatsAppDecoder.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;

    }

    public static String unescapeJavaString(String st) {

        StringBuilder sb = new StringBuilder(st.length());

        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st
                        .charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
                            && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
                                && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                    case '\\':
                        ch = '\\';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case '\"':
                        ch = '\"';
                        break;
                    case '\'':
                        ch = '\'';
                        break;
                    // Hex Unicode: u????
                    case 'u':
                        if (i >= st.length() - 5) {
                            ch = 'u';
                            break;
                        }
                        int code = Integer.parseInt(
                                "" + st.charAt(i + 2) + st.charAt(i + 3)
                                + st.charAt(i + 4) + st.charAt(i + 5), 16);
                        sb.append(Character.toChars(code));
                        i += 5;
                        continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }
}
