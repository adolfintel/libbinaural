/*
 * This library is free software.
 * It is distributed under the GPL License Agreement.
 * http://www.gnu.org/licenses/gpl.html
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.JOptionPane;

/**
 *
 * @author dosse
 */
public class Main {

    public static void main(String args[]) {
        if (args.length == 1 && args[0].equals("-?")) {
            System.out.println("libBinaural.jar -c source.raw      converts a raw 16 bit mono signed little endian audio sample into a noise.dat sample that can be used to replace the noise.dat file built into this .jar file\n"
                    + "libBinaural.jar -? shows this help\n"
                    + "Any other argument or no arguments show an info message.");
        } else if (args.length == 2 && args[0].equals("-c")) {
            try {
                File noiseSample = new File(args[1]);
                FileInputStream fis = new FileInputStream(noiseSample);
                byte[] in = new byte[(int) noiseSample.length()];
                fis.read(in);
                fis.close();
                short[] noise = new short[(int) (noiseSample.length() / 2)];
                for (int i = 0; i < noise.length; i++) {
                    noise[i] = (short) (((in[2 * i + 1] & 0xFF) << 8) | (in[2 * i] & 0xFF));
                }
                ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream("noise.dat")));
                oos.writeObject(noise);
                oos.close();
            } catch (Throwable t) {
                System.out.println("ERROR: " + t);
                return;
            }
            System.out.println("Converted");
        } else {
            JOptionPane.showMessageDialog(new JOptionPane(), "Binaural beats library by dosse91 (dosse91@live.it)\n\n"
                    + "This library allows you to use binaural beats in your application easily.\n"
                    + "It's free, fast, entirely written in java for maximum portability and flexible.\n"
                    + "\n"
                    + "This library is free software, distributed under GPL License.\n"
                    + "\n"
                    + "For any additional information, bug report or feature request, feel free to contact me at dosse91@live.it", "Binaural beats library", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
