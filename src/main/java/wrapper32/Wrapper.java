package wrapper32;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Wrapper {

    public static void main(String[] args) {
        try {
            new Wrapper().run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }


    private Window window;
    private final byte[] buffer = new byte[4096];
    private final List<Memory> memories = new ArrayList<>();

    private void run() throws Exception {
        DataInputStream inputStream = new DataInputStream(new BufferedInputStream(System.in));
        DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(System.out));

        while (true) {
            int cmd = inputStream.readInt();
            if (cmd == -1) {
                break;
            }

            handle(cmd, inputStream, outputStream);
            outputStream.flush();
        }
    }

    private void handle(int cmd, DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        switch (cmd) {
            case 0 -> callNativeFunction(inputStream, outputStream);
            case 1 -> allocateMemory(inputStream, outputStream);
            case 2 -> setMemory(inputStream);
            case 3 -> getMemory(inputStream, outputStream);
            case 4 -> deallocateMemory(inputStream);
            case 5 -> showWindow();
            case 6 -> hideWindow();
        }
    }

    private void hideWindow() {
        if (this.window != null) {
            this.window.setVisible(false);
        }
    }

    private void showWindow() {
        if (this.window == null) {
            this.window = new Window(null);
        }
        this.window.setVisible(true);
    }

    private void deallocateMemory(DataInputStream in) throws IOException {
        int i = in.readInt();
        memories.set(i, null).close();
        System.err.println("deallocateMemory: " + i);
    }

    private void setMemory(DataInputStream in) throws IOException {
        int memoryId = in.readInt();
        Memory memory = memories.get(memoryId);

        int offset = in.readInt();
        int size = in.readInt();

        for (int i = 0; i < size; i += buffer.length) {
            int s = Math.min(buffer.length, size - i);
            in.readFully(buffer, 0, s);
            memory.write(offset + i, buffer, 0, s);
        }
        System.err.println("Set memory: " + memoryId + " " + offset + " " + size);
    }

    private void getMemory(DataInputStream in, DataOutputStream out) throws IOException {
        int i = in.readInt();
        Memory memory = memories.get(i);

        int offset = in.readInt();
        int size = in.readInt();

        for (i = 0; i < size; i += buffer.length) {
            int s = Math.min(buffer.length, size - i);
            memory.read(offset + i, buffer, 0, s);
            out.write(buffer, 0, s);
        }
    }

    private void allocateMemory(DataInputStream in, DataOutputStream out) throws IOException {
        for (int i = 0; i < memories.size(); i++) {
            if (memories.get(i) == null) {
                memories.set(i, new Memory(in.readInt()));
                out.writeInt(i);
                System.err.println("(re)allocateMemory: " + i);
                return;
            }
        }

        memories.add(new Memory(in.readInt()));
        out.writeInt(memories.size() - 1);
        System.err.println("allocateMemory: " + (memories.size() - 1));
    }

    private void callNativeFunction(DataInputStream in, DataOutputStream out) throws IOException {
        String libName = in.readUTF();
        NativeLibrary library = NativeLibrary.getInstance(libName);

        String functionName = in.readUTF();
        Function function = library.getFunction(functionName);
        System.err.println("callNativeFunction: " + libName + "#" + functionName);

        Object[] args = readArgs(in);

        int resultType = in.readInt();
        System.err.println("resultType: " + resultType);
        switch (resultType) {
            case 0 -> {
                function.invokeVoid(args);
                writeResult(out);
            }
            case 1 -> writeResult(out, function.invokeDouble(args));
            case 2 -> writeResult(out, function.invokeFloat(args));
            case 3 -> writeResult(out, function.invokeInt(args));
            case 4 -> writeResult(out, function.invokeLong(args));
            case 5 -> writeResult(out, function.invokeString(args, false));
            default -> throw new IOException("Unknown return type: " + resultType);
        }
        System.err.println("Invoked: "+ functionName);
    }

    private Object[] readArgs(DataInputStream in) throws IOException {
        Object[] args = new Object[in.readInt()];
        System.err.println("Args count: " + args.length);
        for (int i = 0; i < args.length; i++) {
            args[i] = readArg(in);
            System.err.println("Read: " + args[i]);
        }
        return args;
    }

    private Object readArg(DataInputStream in) throws IOException {
        int i = in.readInt();
        if (i <= 0) {
            return memories.get(-i);
        }
        return switch (i) {
            case 1 -> in.readDouble();
            case 2 -> in.readFloat();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readUTF();
            default -> throw new IllegalStateException("Unexpected arg type: " + i);
        };
    }

    private void writeResult(DataOutputStream out) throws IOException {
        out.writeByte(0);
    }

    private void writeResult(DataOutputStream out, int result) throws IOException {
        out.writeInt(result);
    }

    private void writeResult(DataOutputStream out, long result) throws IOException {
        out.writeLong(result);
    }

    private void writeResult(DataOutputStream out, float result) throws IOException {
        out.writeFloat(result);
    }

    private void writeResult(DataOutputStream out, double result) throws IOException {
        out.writeDouble(result);
    }

    private void writeResult(DataOutputStream out, String result) throws IOException {
        out.writeUTF(result);
    }

}
