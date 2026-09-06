package com.gongwen.ooxml

/** OOXML 处理异常：损坏、超限或结构非法时抛出，均带人类可读 message。 */
open class OoxmlException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 输入文件损坏或结构非法。 */
class CorruptPackageException(message: String, cause: Throwable? = null) : OoxmlException(message, cause)

/** 超出安全限额（zip bomb、超量 part、超限解压大小等）。 */
class PackageLimitExceededException(message: String) : OoxmlException(message)
