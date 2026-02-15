package ru.dayagunov.lab_9;

import ru.dayagunov.lab_9.model.TaskRecord;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

public class DataGenerator {
    private static final int TOTAL_RECORDS = 100_000;
    private static final String FILENAME = "tasks_data.bin";

    public static void generateAndSaveData() throws IOException {
        System.out.println("Генерация " + TOTAL_RECORDS + " записей...");

        long startTime = System.currentTimeMillis();
        Random rand = new Random();

        try (FileOutputStream fos = new FileOutputStream(FILENAME);
             FileChannel channel = fos.getChannel()) {

            for (int i = 1; i <= TOTAL_RECORDS; i++) {
                String desc = "Task Description #" + i + " with some random text.";
                boolean completed = rand.nextBoolean();
                int amount = 10 + rand.nextInt(990);

                TaskRecord task = new TaskRecord(i, desc, completed, amount);
                ByteBuffer buffer = task.toByteBuffer();

                // Используем FileChannel для записи
                channel.write(buffer);
            }
            // Принудительно сбрасываем данные на диск
            channel.force(true);
        }
        long endTime = System.currentTimeMillis();
        System.out.printf("Данные сохранены в %s. Время генерации: %d мс\n",
                FILENAME, (endTime - startTime));
    }

    public static void main(String[] args) throws IOException {
        generateAndSaveData();
    }
}