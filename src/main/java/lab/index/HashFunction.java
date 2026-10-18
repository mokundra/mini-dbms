package lab.index;

/**
 * Вычисляет хеши для размещения ключей в индексах.
 */
public class HashFunction {
    private HashFunction(){} //запрет на использование

    /**
     * Вычисляет основной хеш целочисленного ключа.
     *
     * @param key значение ключа
     * @param capacity размер хеш-таблицы
     * @return true, если проверяемое условие выполнено
     */
    public static int hash(long key, int capacity){
        if (capacity<=0){
            throw new IllegalArgumentException("Размер хеш-таблицы должен быть положительным");
        }

        long hash=key;

        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash^= hash >>> 33; //murmurhash3

        return (int) Math.floorMod(hash,capacity);
    }

    /**
     * Вычисляет шаг повторного хеширования ключа.
     *
     * @param key значение ключа
     * @param capacity размер хеш-таблицы
     * @return результат операции
     */
    public static int secondHash(long key, int capacity){
        if (capacity<=1){
            throw new IllegalArgumentException("Размер хеш-таблицы должен быть больше 1");
        }
        long hash = key ^(key >>> 32);
        return 1 + (int) Math.floorMod(hash,capacity-1);
    }
}
