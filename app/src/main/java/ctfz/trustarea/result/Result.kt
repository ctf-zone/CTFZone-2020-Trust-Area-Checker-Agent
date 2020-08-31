package ctfz.trustarea.result


interface Result<T, E> {
    val isOk: Boolean
    val value: T
    val error: E

    fun<T2> flatMap(fn: (T) -> Result<T2, E>): Result<T2, E>
    suspend fun<T2> flatMapA(fn: suspend (T) -> Result<T2, E>): Result<T2, E>
    fun<T2> map(fn: (T) -> T2): Result<T2, E>
    fun<E2> mapErr(fn: (E) -> E2): Result<T, E2>
    fun filter(error: E, fn: (T) -> Boolean): Result<T, E>
}

typealias ResultS<T> = Result<T, String>

fun<T> Result<T, T>.merge(): T {
    if (this.isOk)
        return this.value
    else
        return this.error
}

fun<T, E> resultFromNullable(data: T?, error: E): Result<T, E> {
    if (data == null)
        return Err(error)
    else
        return Ok(data)
}


fun<T, E> T?.asResult(error: E): Result<T, E> = resultFromNullable(this, error)

fun<T, E> T.asOk(): Result<T, E> = Ok(this)
fun<T, E> E.asErr(): Result<T, E> = Err(this)


class Ok<T, E>(private val data: T): Result<T, E> {
    override val isOk = true
    override val value
        get() = data
    override val error: E
        get() = throw RuntimeException("No error in Ok")

    override fun<T2> flatMap(fn: (T) -> Result<T2, E>): Result<T2, E> = fn(value)
    override suspend fun<T2> flatMapA(fn: suspend (T) -> Result<T2, E>): Result<T2, E> = fn(value)
    override fun<T2> map(fn: (T) -> T2): Result<T2, E> = Ok(fn(value))
    override fun<E2> mapErr(fn: (E) -> E2): Result<T, E2> = Ok(value)
    override fun filter(error: E, fn: (T) -> Boolean): Result<T, E> =
        if (fn(value)) this
        else Err(error)
}

class Err<T, E>(private val data: E): Result<T, E> {
    override val isOk = false
    override val value: T
        get() = throw RuntimeException("No error in Ok")
    override val error: E
        get() = data

    override fun <T2> flatMap(fn: (T) -> Result<T2, E>): Result<T2, E> = Err(error)
    override suspend fun <T2> flatMapA(fn: suspend (T) -> Result<T2, E>): Result<T2, E> = Err(error)
    override fun <T2> map(fn: (T) -> T2): Result<T2, E> = Err(error)
    override fun <E2> mapErr(fn: (E) -> E2): Result<T, E2> = Err(fn(error))
    override fun filter(error: E, fn: (T) -> Boolean): Result<T, E> = this
}


fun<T, T2, R, E> mapN(res1: Result<T, E>, res2: Result<T2, E>, fn: (T, T2) -> R): Result<R, E> =
    res1.flatMap { val1 ->
        res2.map { val2 ->
            fn(val1, val2)
        }
    }

fun<T, T2, T3, R, E> mapN(res1: Result<T, E>, res2: Result<T2, E>, res3: Result<T3, E>, fn: (T, T2, T3) -> R): Result<R, E> =
    res1.flatMap { val1 ->
        mapN<T2, T3, R, E>(res2, res3) { val2, val3 ->
            fn(val1, val2, val3)
        }
    }

fun<T, T2, T3, T4, R, E> mapN(res1: Result<T, E>, res2: Result<T2, E>, res3: Result<T3, E>, res4: Result<T4, E>, fn: (T, T2, T3, T4) -> R): Result<R, E> =
    res1.flatMap { val1 ->
        mapN<T2, T3, T4, R, E>(res2, res3, res4) { val2, val3, val4 ->
            fn(val1, val2, val3, val4)
        }
    }

fun<T, T2, T3, T4, T5, R, E> mapN(res1: Result<T, E>, res2: Result<T2, E>, res3: Result<T3, E>, res4: Result<T4, E>, res5: Result<T5, E>, fn: (T, T2, T3, T4, T5) -> R): Result<R, E> =
    res1.flatMap { val1 ->
        mapN<T2, T3, T4, T5, R, E>(res2, res3, res4, res5) { val2, val3, val4, val5 ->
            fn(val1, val2, val3, val4, val5)
        }
    }

fun<T, T2, T3, T4, T5, T6, R, E> mapN(res1: Result<T, E>, res2: Result<T2, E>, res3: Result<T3, E>, res4: Result<T4, E>, res5: Result<T5, E>, res6: Result<T6, E>, fn: (T, T2, T3, T4, T5, T6) -> R): Result<R, E> =
    res1.flatMap { val1 ->
        mapN<T2, T3, T4, T5, T6, R, E>(res2, res3, res4, res5, res6) { val2, val3, val4, val5, val6 ->
            fn(val1, val2, val3, val4, val5, val6)
        }
    }


fun<T, T2, R, E> flatMapN(res1: Result<T, E>, res2: Result<T2, E>, fn: (T, T2) -> Result<R, E>): Result<R, E> =
    res1.flatMap { val1 ->
       res2.flatMap { val2 ->
           fn(val1, val2)
       }
    }

fun<T, T2, T3, R, E> flatMapN(res1: Result<T, E>, res2: Result<T2, E>, res3: Result<T3, E>, fn: (T, T2, T3) -> Result<R, E>): Result<R, E> =
    res1.flatMap { val1 ->
        flatMapN<T2, T3, R, E>(res2, res3) { val2, val3 ->
            fn(val1, val2, val3)
        }
    }
