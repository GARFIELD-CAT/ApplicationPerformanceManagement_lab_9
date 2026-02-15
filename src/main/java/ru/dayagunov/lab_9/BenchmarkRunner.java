package ru.dayagunov.lab_9;

import ru.dayagunov.lab_9.model.TaskRecord;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {

    private static final String FILENAME = "tasks_data.bin";
    private static final int RECORD_SIZE = TaskRecord.getRecordSize();
    private static final int[] RECORD_COUNTS = {10_000, 50_000, 100_000};

    private static long fileSize;

    public static void main(String[] args) throws Exception {
        // Шаг 1: Генерация данных (если файла нет)
        if (!new File(FILENAME).exists()) {
            DataGenerator.generateAndSaveData();
        }

        fileSize = Files.size(Path.of(FILENAME));
        System.out.println("\n--- НАЧАЛО ТЕСТИРОВАНИЯ IO ---");
        System.out.println("Размер файла: " + fileSize / (1024 * 1024) + " MB");

        // Запуск тестов для каждого способа
        for (int count : RECORD_COUNTS) {
            System.out.println("\n=== Тестирование на " + count + " записях ===");

            // Тест RAF
            testRandomAccessFile(count);

            // Тест FileChannel
            testFileChannel(count);

            // Тест MappedByteBuffer
            testMappedByteBuffer(count);
        }
    }

    // ==============================================================
    // СПОСОБ A: RandomAccessFile (RAF)
    // ==============================================================

    private static void testRandomAccessFile(int recordCount) throws IOException {
        long totalTime = 0;

        // 1. Тест записи (Последовательная запись)
        long startWrite = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(FILENAME, "rw")) {
            for (int i = 0; i < recordCount; i++) {
                long offset = i * RECORD_SIZE;
                raf.seek(offset);

                // В RAF мы должны вручную подготовить данные для записи
                TaskRecord task = new TaskRecord(i, "RAF Test " + i, i % 2 == 0, 100 + i);

                // Ручная запись (имитация записи по смещению)
                raf.writeInt(task.getId());
                raf.writeBoolean(task.getCompleted());
                raf.writeInt(task.getAmount());
                raf.writeBytes(String.format("%-100s", task.getDescription()).substring(0, 100));
            }
            raf.getFD().sync(); // Принудительная синхронизация
        }
        totalTime = System.nanoTime() - startWrite;
        System.out.printf("A (RAF) Запись (%d): %.3f мс\n", recordCount, ms(totalTime));


        // 2. Тест чтения (Случайное чтение по смещению)
        long startRead = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(FILENAME, "r")) {
            Random random = new Random();
            for (int i = 0; i < recordCount; i++) {
                long offset = random.nextInt((int)fileSize / RECORD_SIZE) * RECORD_SIZE;
                raf.seek(offset);

                // Чтение
                raf.readInt(); // id
                raf.readBoolean(); // completed
                raf.readInt(); // amount
                byte[] descBuffer = new byte[100];
                raf.read(descBuffer);
                // Игнорируем результат для чистоты замера
            }
        }
        totalTime = System.nanoTime() - startRead;
        System.out.printf("A (RAF) Случайное чтение (%d): %.3f мс\n", recordCount, ms(totalTime));
    }

    // ==============================================================
    // СПОСОБ B: FileChannel (ByteBuffer Write/Force)
    // ==============================================================

    private static void testFileChannel(int recordCount) throws IOException {
        // FileChannel используется для пакетной записи

        // 1. Тест записи (Пакетная запись)
        long startWrite = System.nanoTime();
        try (FileChannel channel = FileChannel.open(Path.of(FILENAME),
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {

            ByteBuffer buffer = ByteBuffer.allocate(recordCount * RECORD_SIZE);

            for (int i = 0; i < recordCount; i++) {
                TaskRecord task = new TaskRecord(i, "Channel Test " + i, i % 3 == 0, 200 + i);
                buffer.put(task.toByteBuffer()); // Сборка всех данных в один буфер
            }
            buffer.flip();

            channel.write(buffer);
            channel.force(true); // Синхронизация с диском
        }
        long totalTime = System.nanoTime() - startWrite;
        System.out.printf("B (Channel) Пакетная запись (%d): %.3f мс\n", recordCount, ms(totalTime));


        // 2. Тест чтения (Последовательное чтение)
        long startRead = System.nanoTime();
        try (FileChannel channel = FileChannel.open(Path.of(FILENAME), StandardOpenOption.READ)) {
            ByteBuffer readBuffer = ByteBuffer.allocate(RECORD_SIZE);

            for (int i = 0; i < recordCount; i++) {
                channel.read(readBuffer);
                readBuffer.flip();
                // Десериализация (для демонстрации, что данные читаются)
                new TaskRecord(readBuffer);
                readBuffer.clear();
            }
        }
        totalTime = System.nanoTime() - startRead;
        System.out.printf("B (Channel) Последовательное чтение (%d): %.3f мс\n", recordCount, ms(totalTime));
    }

    // ==============================================================
    // СПОСОБ C: Memory-Mapped File (MappedByteBuffer)
    // ==============================================================

    private static void testMappedByteBuffer(int recordCount) throws IOException {

        // 1. Тест записи (Случайная запись через маппинг)
        long startWrite = System.nanoTime();
        try (FileChannel channel = FileChannel.open(Path.of(FILENAME),
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {

            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);

            for (int i = 0; i < recordCount; i++) {
                int position = i * RECORD_SIZE;

                // Проверка, что мы не выходим за пределы
                if (position + RECORD_SIZE > fileSize) break;

                mbb.position(position);

                TaskRecord task = new TaskRecord(i + 100000, "MMap Test " + i, i % 4 == 0, 500 + i);

                // Запись напрямую в MappedByteBuffer
                ByteBuffer buffer = task.toByteBuffer();
                mbb.put(buffer);
            }
            // Необязательно вызывать force, но рекомендуется для гарантии
            mbb.force();
        }
        long totalTime = System.nanoTime() - startWrite;
        System.out.printf("C (MMap) Случайная запись (%d): %.3f мс\n", recordCount, ms(totalTime));

        // 2. Тест чтения (Случайное чтение через маппинг)
        long startRead = System.nanoTime();
        try (FileChannel channel = FileChannel.open(Path.of(FILENAME), StandardOpenOption.READ)) {

            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            Random random = new Random();

            for (int i = 0; i < recordCount; i++) {
                int position = random.nextInt((int)fileSize / RECORD_SIZE) * RECORD_SIZE;

                mbb.position(position);

                // Чтение
                new TaskRecord(mbb);
            }
        }
        totalTime = System.nanoTime() - startRead;
        System.out.printf("C (MMap) Случайное чтение (%d): %.3f мс\n", recordCount, ms(totalTime));
    }

    private static double ms(long ns) {
        return TimeUnit.NANOSECONDS.toMillis(ns) / 1000.0;
    }
}