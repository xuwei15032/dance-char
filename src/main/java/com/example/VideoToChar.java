package com.example;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @email: xuwei15032@qq.com
 */
public class VideoToChar {

    /**
     * 前面的字符笔画多，后面的字符笔画少
     */
    public final static String ASCII_CHAR = "$@B%8&WM#*oahkbdpqwmZO0QLCJUYXzcvunxrjft/\\|()1{}[]?-_+~<>i!lI;:,\"^`'. ";

    private static final Logger LOGGER = Logger.getLogger(VideoToChar.class.getName());

    public VideoToChar() {
        LOGGER.setLevel(Level.INFO);
    }

    /**
     * videoPath: 视频路径
     * outPath: 输出路径
     * outName: 输出文件名
     * charWidthNum: 字符宽度倍数，即每行字符数
     * 例如：charWidthNum=50，则每行字符数为50个
     * fontSize: 字体大小
     * 
     * 根据视频分辨率大小手动调节charWidthNum和fontSize
     * 
     */
    public void exec(String videoPath, String outPath, String outName, int charWidthNum, int fontSize)
            throws Exception {

        String cachePath = outPath + "/cache";

        LOGGER.info("开始分割视频为字符图片");
        int imgNum = mp4ToCharImg(cachePath, videoPath, outPath, outName, charWidthNum, fontSize);

        LOGGER.info("图片转成字符图片完成,开始合成视频");
        imgsToMp4(cachePath, outPath + "/" + outName, imgNum, videoPath);
        LOGGER.info("合成视频完成： " + outPath + "/" + outName);
    }

    private int mp4ToCharImg(String cachePath, String videoPath, String outPath, String outName, int charWidthNum,
            int fontSize)
            throws Exception {
                
        ExecutorService executor = Executors.newFixedThreadPool(32);
        int imgIndex = 0;
        List<Future<?>> futures = new ArrayList<>();

        // 视频分割为img
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            grabber.start();
            Frame frame;
            File cacheDir = new File(cachePath);
            LOGGER.info("临时文件地址: " + cachePath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            deleteFilesInDirectory(cacheDir);
            char[][] charArray = null;
            while ((frame = grabber.grabFrame()) != null) {
                // 转换Frame为BufferedImage
                Java2DFrameConverter converter = new Java2DFrameConverter();
                BufferedImage bufferedImage = converter.convert(frame);
                if (bufferedImage != null) {
                    charArray = imgToGray(bufferedImage, charWidthNum);
                    BufferedImage resImg = getImageByCharArray(charArray, grabber.getImageWidth(),
                            grabber.getImageHeight(), charWidthNum, fontSize);
                    final int imgIndexF = imgIndex;
                    Future<?> future = executor.submit(() -> {
                        try {
                            saveImg(cachePath + "/output" + imgIndexF + ".jpg", resImg);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    futures.add(future);
                    imgIndex++;
                }
            }
            for (Future<?> future : futures) {
                future.get(); // 等待每个保存图片任务完成
            }
            executor.shutdown();
            grabber.stop();
            grabber.release();
            return imgIndex;
        }
    }

    /**
     * 用字符图片合成视频
     */
    private void imgsToMp4(String cachePath, String desPath, int imgNum, String sourcePath) throws IOException {
        try (FFmpegFrameGrabber originGrabber = new FFmpegFrameGrabber(sourcePath)) {
            originGrabber.start();
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(desPath, originGrabber.getImageWidth(),
                    originGrabber.getImageHeight(), originGrabber.getAudioChannels())) {
                Frame frame;
                recorder.setVideoCodec(originGrabber.getVideoCodec());
                recorder.setAudioCodec(originGrabber.getAudioCodec());
                recorder.setFormat("mp4");
                recorder.setSampleRate(originGrabber.getSampleRate());
                recorder.setFrameRate(originGrabber.getFrameRate());
                recorder.setVideoBitrate(originGrabber.getVideoBitrate() * 10);
                recorder.start();

                // 遍历BufferedImage列表，并将每个图像转换为Frame然后记录
                for (int imgIndex = 0; imgIndex < imgNum; imgIndex++) {
                    File imgFile = new File(cachePath + "/output" + imgIndex + ".jpg");
                    BufferedImage image = ImageIO.read(imgFile);
                    Java2DFrameConverter converter = new Java2DFrameConverter();
                    frame = converter.convert(image);
                    recorder.record(frame);
                }

                // 音频
                while ((frame = originGrabber.grabFrame()) != null) {
                    if (frame != null && frame.image == null && frame.samples != null) {
                        recorder.record(frame);
                    }
                }
                recorder.stop();
                recorder.release();
            }
            originGrabber.stop();
            originGrabber.release();
        }
    }

    // 图片转为灰度图片
    private char[][] imgToGray(BufferedImage image, int charWidthNum) {
        char[][] origin = new char[image.getHeight()][image.getWidth()];
        char[][] result = new char[image.getHeight() * charWidthNum / image.getWidth() + 1][charWidthNum];

        // 遍历原始图片的每个像素，并计算灰度值
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = (rgb) & 0xff;
                int gray = (r + g + b) / 3;
                origin[y][x] = getCharFromGray(gray);
            }
        }
        // 每个像素一个字符太密集了，每一行charWidthNum个字符按比例从原始像素中取
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[i].length; j++) {
                result[i][j] = origin[i * image.getHeight() / result.length][j * image.getWidth() / result[0].length];
            }
        }
        return result;
    }

    /**
     * 
     * 通过字符获取图片
     */
    private BufferedImage getImageByCharArray(char[][] charArray, int originWidth, int originHeight, int charWidthNum,
            int fontSize) {
        BufferedImage image = new BufferedImage(originWidth, originHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, originWidth, originHeight);
        Font font = new Font("Serif", Font.PLAIN, fontSize);
        g2d.setColor(Color.BLACK);
        g2d.setFont(font);
        for (int i = 0; i < charArray.length; i++) {
            for (int j = 0; j < charArray[i].length; j++) {
                float x = (j * (originWidth * 1.0f / charWidthNum));
                float y = i * (originHeight * 1.0f / ((originHeight * charWidthNum * 1.0f / originWidth)));
                g2d.drawString(charArray[i][j] + "", x, y);
            }
        }
        g2d.dispose();
        return image;
    }

    /**
     * 通过灰度值获取对应字符
     */
    private char getCharFromGray(Integer gray) {
        double index = (gray + 1) / 256.0 * (ASCII_CHAR.length() - 1);
        return ASCII_CHAR.charAt((int) Math.floor(index));
    }

    /**
     * 删除临时文件
     */
    private void deleteFilesInDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) {
                    boolean success = file.delete();
                    if (!success) {
                        LOGGER.warning("文件删除失败: " + file.getPath());
                    }
                }
            }
        }
    }

    /**
     * 将图片保存到文件
     */
    private void saveImg(String path, BufferedImage img) throws FileNotFoundException, IOException {
        File outputFile = new File(path);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = (ImageWriter) writers.next();
        FileImageOutputStream ios = new FileImageOutputStream(outputFile);
        writer.setOutput(ios);
        // 创建ImageWriteParam对象并设置压缩质量
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.1f);
        writer.write(null, new IIOImage(img, null, null), param);
        ios.close();
        writer.dispose();
    }
}
