package com.lightning.editor;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;

import javax.imageio.ImageIO;

public class Main {
    private static BufferedImage[] tiles;
    private static int tileSize;
    private static boolean saved = true;
    private static int xScroll = 0, yScroll = 0;
    private static int xPos = 0, yPos = 0;
    private static String filename = null;
    
    private static ArrayList<ArrayList<Byte>> curLevel = null;
    
    public static void main(String[] args) throws IOException {
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        Screen screen = new TerminalScreen(terminal);
        TextGraphics tGraphics = screen.newTextGraphics();

        screen.startScreen();
        screen.clear();
        
        {
            tGraphics.setBackgroundColor(new TextColor.RGB(0, 0, 0));
            tGraphics.setForegroundColor(new TextColor.RGB(255, 255, 255));
            tGraphics.putString(0, 0, "Loading...");
            screen.refresh();
            
            BufferedImage tileset = ImageIO.read(new File("assets/tileset.png"));
            tiles = new BufferedImage[256];
            tileSize = tileset.getWidth()/16;
            for(int x = 0; x < 16; x++) {
                for(int y = 0; y < 16; y++) {
                    tiles[x+y*16] = tileset.getSubimage(x*tileSize, y*tileSize, tileSize, tileSize);
                }
            }
        }
        
        screen.setCursorPosition(new TerminalPosition(0, 0));
        
        boolean quit = false;
        int mode = 0; // Quick Mode
        while(!quit) {
            TerminalSize termSize = tGraphics.getSize();
            int width = termSize.getColumns();
            int height = termSize.getRows();
            StringBuilder clearBuilder = new StringBuilder();
            for(int i = 0; i < width; i++) {
                clearBuilder.append(' ');
            }
            String clearRow = clearBuilder.toString();
            
            renderLevel(width, height, clearRow, tGraphics);
            
            screen.refresh();
            
            if(mode == 0) { // Quick mode
                KeyStroke result = screen.readInput();
                if(result.getKeyType() == KeyType.Escape) {
                    // do nothing
                } else if(result.getKeyType() == KeyType.ArrowLeft) {
                    if(xPos <= xScroll) {
                        xScroll-=2;
                        renderLevel(width, height, clearRow, tGraphics);
                    }
                    xPos--;
                } else if(result.getKeyType() == KeyType.ArrowRight) {
                    xPos++;
                    if(xPos >= xScroll+width) {
                        xScroll+=2;
                        renderLevel(width, height, clearRow, tGraphics);
                    }
                } else if(result.getKeyType() == KeyType.ArrowUp) {
                    if(yPos == yScroll) {
                        yScroll--;
                        renderLevel(width, height, clearRow, tGraphics);
                    }
                    yPos--;
                } else if(result.getKeyType() == KeyType.ArrowDown) {
                    yPos++;
                    if(yPos == yScroll+height) {
                        yScroll++;
                        renderLevel(width, height, clearRow, tGraphics);
                    }
                } else if(result.getKeyType() != KeyType.Character) {
                    // Do nothing
                } else if(result.getCharacter() == ':') {
                    mode = 1; // Command Mode
                } else if(result.getCharacter() == 'i') {
                    if(curLevel != null)
                        mode = 2; // Insert Mode
                    else {
                        tGraphics.putString(0, height-1, "Can't go to insert mode: no active file!");
                    }
                } else {
                    tGraphics.putString(0, height-1, "Unrecognized command '" + result.getCharacter() + "'");
                }
            } else if(mode == 1) { // Command Mode
                tGraphics.putString(0, height-1, clearRow);
                tGraphics.putString(0, height-1, ":");
                screen.setCursorPosition(new TerminalPosition(1, height-1));
                KeyStroke result;
                StringBuilder commandBuilder = new StringBuilder();
                do {
                    screen.refresh();
                    result = screen.readInput();
                    TerminalPosition newPos = screen.getCursorPosition();
                    for(int i = newPos.getColumn(); i < width; i++) {
                        tGraphics.putString(i, height-1, " ");
                    }
                    if(result.getKeyType() == KeyType.Backspace) {
                        newPos = newPos.withRelativeColumn(-1);
                        if(newPos.getColumn() == 0) {
                            mode = 0; // exit from command mode
                            break;
                        }
                        tGraphics.putString(newPos, " ");
                        screen.setCursorPosition(newPos);
                        commandBuilder.deleteCharAt(commandBuilder.length()-1);
                    } else if(result.getKeyType() == KeyType.Tab) {
                        tGraphics.putString(newPos, " <I can't autocomplete, sorry>");
                        screen.setCursorPosition(newPos);
                    } else if(result.getKeyType() != KeyType.Enter) {
                        tGraphics.putString(newPos, new String(new char[] { result.getCharacter() }));
                        screen.setCursorPosition(newPos = newPos.withRelativeColumn(1));
                        commandBuilder.append(result.getCharacter());
                    }
                } while(result.getKeyType() != KeyType.Enter);
                String command = commandBuilder.toString();
                if(command.equals("")) {
                    // do nothing
                } else if(command.equals("i") || command.equals("image")) {
                    if(curLevel == null)
                        tGraphics.putString(0, height-1, "Can't save an image: no active file!");
                    else {
                        tGraphics.putString(0, height-1, "Saving...");
                        screen.refresh();
                        if(saveImage()) {
                            tGraphics.putString(0, height-1, "Saved!   ");
                            screen.refresh();
                        } else {
                            tGraphics.putString(0, height-1, "Not saved!");
                            screen.refresh();
                        }
                    }
                } else if(command.startsWith("l")) {
                    if(saved) {
                        tGraphics.putString(0, height-1, "Loading...");
                        screen.refresh();
                        if(command.length() > 1) {
                            command = command.substring(1);
                            if(command.charAt(0) != ' ')
                                command = command.substring(3);
                            if(command.length() > 0) {
                                filename = command.substring(1);
                            }
                        }
                        if(loadLevel()) {
                            tGraphics.putString(0, height-1, "Loaded!  ");
                            screen.refresh();
                        } else {
                            tGraphics.putString(0, height-1, "Load failed! Check logs for explanation.");
                            screen.refresh();
                        }
                    } else {
                        tGraphics.putString(0, height-1, "Not saved! (Use `:" + command + "!` to override)");
                    }
                } else if(command.equals("n") || command.equals("new")) {
                    if(saved) {
                        saved = false;
                        curLevel = new ArrayList<>();
                        tGraphics.putString(0, height-1, "New level created!");
                    } else {
                        tGraphics.putString(0, height-1, "Not saved! (Use `:" + command + "!` to override)");
                    }
                } else if(command.equals("n!") || command.equals("new!")) {
                    saved = false;
                    curLevel = new ArrayList<>();
                    tGraphics.putString(0, height-1, "New level created!");
                } else if(command.equals("q") || command.equals("quit")) {
                    if(saved) {
                        quit = true;
                    } else {
                        tGraphics.putString(0, height-1, "Not saved! (Use `:" + command + "!` to override)");
                    }
                } else if(command.equals("q!") || command.equals("quit!")) {
                    quit = true;
                } else if(command.equals("v") || command.equals("version")) {
                    tGraphics.putString(0, height-1, "LeVIm v1.0 by Ray Redondo (C) 2019 Lightning Creations");
                } else if(command.startsWith("w")) {
                    tGraphics.putString(0, height-1, clearRow);
                    if(curLevel == null)
                        tGraphics.putString(0, height-1, "Can't save: no active file!");
                    else {
                        tGraphics.putString(0, height-1, "Saving...");
                        screen.refresh();
                        if(command.length() > 1) {
                            command = command.substring(1);
                            if(command.charAt(0) != ' ')
                                command = command.substring(3);
                            if(command.length() > 0) {
                                filename = command.substring(1);
                            }
                        }
                        if(saveLevel()) {
                            tGraphics.putString(0, height-1, "Saved!   ");
                            screen.refresh();
                        } else {
                            tGraphics.putString(0, height-1, "Save failed! Check logs for explanation.");
                            screen.refresh();
                        }
                    }
                } else {
                    tGraphics.putString(0, height-1, "E492: Not an editor command: " + command + "");
                }
                mode = 0;
                screen.setCursorPosition(new TerminalPosition(xPos-xScroll, yPos-yScroll));
            } else if(mode == 2) { // Insert Mode
                tGraphics.putString(0, height-1, clearRow);
                tGraphics.putString(0, height-1, "-- INSERT --");
                screen.refresh();
                KeyStroke result;
                TerminalPosition newPos = new TerminalPosition(xPos-xScroll, yPos-yScroll);
                do {
                    result = screen.readInput();
                    if(result.getKeyType() == KeyType.ArrowLeft) {
                        if(xPos <= xScroll) {
                            xScroll-=2;
                            renderLevel(width, height, clearRow, tGraphics);
                        }
                        xPos--;
                        screen.setCursorPosition(newPos = new TerminalPosition(xPos-xScroll, yPos-yScroll));
                    } else if(result.getKeyType() == KeyType.ArrowRight) {
                        xPos++;
                        if(xPos >= xScroll+width) {
                            xScroll+=2;
                            renderLevel(width, height, clearRow, tGraphics);
                        }
                        screen.setCursorPosition(newPos = new TerminalPosition(xPos-xScroll, yPos-yScroll));
                    } else if(result.getKeyType() == KeyType.ArrowUp) {
                        if(yPos == yScroll) {
                            yScroll--;
                            renderLevel(width, height, clearRow, tGraphics);
                        }
                        yPos--;
                        screen.setCursorPosition(newPos = new TerminalPosition(xPos-xScroll, yPos-yScroll));
                    } else if(result.getKeyType() == KeyType.ArrowDown) {
                        yPos++;
                        if(yPos == yScroll+height) {
                            yScroll++;
                            renderLevel(width, height, clearRow, tGraphics);
                        }
                        screen.setCursorPosition(newPos = new TerminalPosition(xPos-xScroll, yPos-yScroll));
                    } else if(result.getKeyType() == KeyType.Backspace) {
                        if(xPos <= xScroll) {
                            xScroll-=2;
                        }
                        xPos--;
                        setChar(xPos, yPos, '0');
                        screen.setCursorPosition(newPos = new TerminalPosition(xPos-xScroll, yPos-yScroll));
                        renderLevel(width, height, clearRow, tGraphics);
                        saved = false;
                    } else if(result.getKeyType() == KeyType.Escape) {
                        tGraphics.putString(0, height-1, clearRow);
                    } else if(result.getKeyType() == KeyType.Tab) {
                        // Do nothing
                    } else if(result.getKeyType() == KeyType.Enter) {
                        yPos++;
                        if(yPos == yScroll+height) {
                            yScroll++;
                            renderLevel(width, height, clearRow, tGraphics);
                        }
                        screen.setCursorPosition(newPos = new TerminalPosition(xPos-xScroll, yPos-yScroll));
                    } else if(result.getKeyType() != KeyType.Character) {
                        // Do nothing for now
                    } else if((result.getCharacter() <= '9' && result.getCharacter() >= '0') || (result.getCharacter() <= 'F' && result.getCharacter() >= 'A') || (result.getCharacter() <= 'f' && result.getCharacter() >= 'a')) {
                        setChar(xPos, yPos, result.getCharacter());
                        xPos++;
                        if(xPos == xScroll+width) {
                            xScroll+=2;
                            screen.setCursorPosition(newPos = newPos.withRelativeColumn(-1));
                        } else
                            screen.setCursorPosition(newPos = newPos.withRelativeColumn(1));
                        saved = false;
                        renderLevel(width, height, clearRow, tGraphics);
                    }
                    screen.refresh();
                } while(result.getKeyType() != KeyType.Escape);
                mode = 0;
            }
        }

        screen.stopScreen();
        screen.close();
    }
    
    public static boolean saveImage() {
        try {
            int width = curLevel.size() * tileSize;
            
            int height = 0;
            for(int i = 0; i < curLevel.size(); i++) {
                int curHeight = curLevel.get(i).size();
                if(curHeight > height)
                    height = curHeight;
            }
            height *= tileSize;
            
            BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D graphics = output.createGraphics();
            graphics.fillRect(0, 0, width, height);
            
            for(int i = 0; i < curLevel.size(); i++)
                for(int j = 0; j < curLevel.get(i).size(); j++)
                    graphics.drawImage(tiles[curLevel.get(i).get(j)], i*tileSize, j*tileSize, tileSize, tileSize, null);
            
            ImageIO.write(output, "PNG", new File("level.png"));
            
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void renderLevel(int width, int height, String clearRow, TextGraphics tGraphics) {
        // Clear level area
        for(int i = 0; i < height-1; i++) {
            tGraphics.putString(0, i, clearRow);
        }
        if(curLevel != null) {
            for(int i = 0; i < width; i+=2) {
                for(int j = 0; j < height-1; j++) {
                    int xPosition = (xScroll+i)/2;
                    int yPosition = yScroll+j;
                    if(xPosition < 0 || yPosition < 0 || xPosition >= curLevel.size() || yPosition >= curLevel.get(xPosition).size())
                        continue;
                    byte value = curLevel.get(xPosition).get(yPosition);
                    if(value == 0) continue;
                    tGraphics.putString(i, j, String.format("%02X", value));
                }
            }
        }
    }
    
    public static void setChar(int x, int y, char value) {
        int xIndex = x/2;
        while(xIndex < 0) {
            xIndex++;
            xScroll += 2;
            xPos += 2;
            curLevel.add(0, new ArrayList<>());
        }
        while(curLevel.size() <= xIndex) {
            curLevel.add(new ArrayList<>());
        }
        ArrayList<Byte> curRow = curLevel.get(xIndex);
        
        int yIndex = y;
        while(yIndex < 0) {
            yIndex++;
            yScroll++;
            yPos++;
            curRow.add(0, (byte) 0);
        }
        while(curRow.size() <= yIndex) {
            curRow.add((byte) 0);
        }
        
        byte trueValue;
        if(value >= '0' && value <= '9') {
            trueValue = (byte) (value - '0');
        } else if(value >= 'A' && value <= 'F') {
            trueValue = (byte) (value - 'A' + 10);
        } else if(value >= 'a' && value <= 'f') {
            trueValue = (byte) (value - 'a' + 10);
        } else {
            trueValue = 0;
        }
        
        byte prevValue = curRow.get(yIndex);
        if(x % 2 == 0) {
            prevValue &= 0x0F;
            prevValue |= trueValue << 4;
        } else {
            prevValue &= 0xF0;
            prevValue |= trueValue;
        }
        curRow.set(yIndex, prevValue);
    }
    
    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        
        byte[] buffer = new byte[1024];
        int len;
        
        // read bytes from the input stream and store them in buffer
        while ((len = in.read(buffer)) != -1) {
            // write bytes from the buffer into output stream
            os.write(buffer, 0, len);
        }
        
        return os.toByteArray();
    }
    
    public static boolean loadLevel() {
        try {
            if(filename == null) {
                return false;
            }
            FileInputStream inFile = new FileInputStream(filename);
            byte[] in = toByteArray(inFile);
            
            if(in[0] != 0x11 || in[1] != 0x54 || in[2] != 0x23 || in[3] != (byte) 0xF4) // Wrong magic
                return false;
            
            if(in[4] > 0x00 || in[5] > 0x00) // File is too new
                return false;
            
            int i = 6;
            int numLayers = in[i++] & 0x00FF;
            if(numLayers != 1) // Not compatible with the editor
                return false;
            
            ArrayList<ArrayList<Byte>> newLevel = new ArrayList<>();
            int numCols = ((in[i++] & 0x00FF) << 8) | (in[i++] & 0x00FF);
            for(int j = 0; j < numCols; j++) {
                int numCells = in[i++] & 0xFF;
                ArrayList<Byte> curCol = new ArrayList<>();
                newLevel.add(curCol);
                for(int k = 0; k < numCells; k++) {
                    curCol.add(in[i++]);
                }
            }
            
            i += 8; // Skip scroll speed, numSprites, and numTriggers
//            MessageDigest sha = MessageDigest.getInstance("SHA-256"); // Commented out due to hash checking malfunctions
//            sha.update(in, 0, i);
//            byte[] sig = sha.digest();
//            for(int j = 0; j < 32; j++) {
//                if(sig[j] != in[i+j]) { // Signature mismatch
//                    return false;
//                }
//            }
            
            curLevel = newLevel;
            
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean saveLevel() {
        try {
            if(filename == null) {
                return false;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Save curLevel
            out.write(new byte[] {0x11, 0x54, 0x23, (byte) 0xF4}); // Magic
            out.write(new byte[] {0x00, 0x00}); // Version
            
            out.write(1); // 1 layer
            int numColumns = curLevel.size();
            out.write(numColumns >> 8);
            out.write(numColumns & 0xFF);
            for(ArrayList<Byte> curRow : curLevel) {
                out.write(curRow.size());
                for(Byte cur: curRow) {
                    out.write(cur);
                }
            }
            out.write(Float.floatToIntBits(1)); // Background scroll speed relative to player

            out.write(new byte[] {0, 0}); // No sprites

            out.write(new byte[] {0, 0}); // No triggers
            
            out.close();
            
            byte[] data1 = out.toByteArray();
            
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] data2 = sha.digest(data1);
            
            FileOutputStream fileOut = new FileOutputStream(filename);
            fileOut.write(data1);
            fileOut.write(data2);
            fileOut.close();
            
            saved = true;
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}