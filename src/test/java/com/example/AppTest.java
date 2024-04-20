package com.example;

import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     * @throws Exception 
     */
    @Test
    public void shouldAnswerWithTrue() throws Exception
    {
        VideoToChar videoToChar = new VideoToChar();
        videoToChar.exec(
                "C:/Users/22609/Downloads/aa.mp4",
                "C:/Users/22609/Downloads",
                "output.mp4",
                50,
                20);
    }
}
