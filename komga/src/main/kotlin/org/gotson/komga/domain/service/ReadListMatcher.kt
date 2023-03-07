package org.gotson.komga.domain.service

import mu.KotlinLogging
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadListMatch
import org.gotson.komga.domain.model.ReadListRequest
import org.gotson.komga.domain.model.ReadListRequestBookMatches
import org.gotson.komga.domain.model.ReadListRequestMatch
import org.gotson.komga.domain.model.ReadListRequestResult
import org.gotson.komga.domain.model.ReadListRequestResultBook
import org.gotson.komga.domain.persistence.BookMetadataRepository
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.domain.persistence.ReadListRepository
import org.gotson.komga.domain.persistence.SeriesRepository
import org.gotson.komga.language.toIndexedMap
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ReadListMatcher(
  private val readListRepository: ReadListRepository,
  private val seriesRepository: SeriesRepository,
  private val bookRepository: BookRepository,
  private val bookMetadataRepository: BookMetadataRepository,
) {

  fun matchAndCreateReadListRequest(request: ReadListRequest): ReadListRequestResult {
    logger.info { "Trying to match $request" }
    if (readListRepository.existsByName(request.name)) {
      return ReadListRequestResult(readList = null, unmatchedBooks = request.books.map { ReadListRequestResultBook(it) }, errorCode = "ERR_1009")
    }

    val bookIds = mutableListOf<String>()
    val unmatchedBooks = mutableListOf<ReadListRequestResultBook>()

    request.books.forEach { book ->
      val seriesMatches = seriesRepository.findAllByTitle(book.series.first())
      when {
        seriesMatches.size > 1 -> unmatchedBooks += ReadListRequestResultBook(book, "ERR_1011")
        seriesMatches.isEmpty() -> unmatchedBooks += ReadListRequestResultBook(book, "ERR_1012")
        else -> {
          val seriesId = seriesMatches.first().id
          val seriesBooks = bookRepository.findAllBySeriesId(seriesId)
          val bookMatches = bookMetadataRepository.findAllByIds(seriesBooks.map { it.id })
            .filter { (it.number.trimStart('0') == book.number.trimStart('0')) }
            .map { it.bookId }
          when {
            bookMatches.size > 1 -> unmatchedBooks += ReadListRequestResultBook(book, "ERR_1013")
            bookMatches.isEmpty() -> unmatchedBooks += ReadListRequestResultBook(book, "ERR_1014")
            bookIds.contains(bookMatches.first()) -> unmatchedBooks += ReadListRequestResultBook(book, "ERR_1023")
            else -> bookIds.add(bookMatches.first())
          }
        }
      }
    }

    return if (bookIds.isNotEmpty())
      ReadListRequestResult(
        readList = ReadList(name = request.name, bookIds = bookIds.toIndexedMap()),
        unmatchedBooks = unmatchedBooks,
      )
    else {
      ReadListRequestResult(
        readList = null,
        unmatchedBooks = unmatchedBooks,
        errorCode = "ERR_1010",
      )
    }
  }

  fun matchReadListRequest(request: ReadListRequest): ReadListRequestMatch {
    logger.info { "Trying to match $request" }

    val readListMatch =
      if (readListRepository.existsByName(request.name)) ReadListMatch(request.name, "ERR_1009")
      else ReadListMatch(request.name)

    val matches = request.books.map { book ->
      val matches = book.series.flatMap { seriesRepository.findAllByTitle(it) }.associateWith { series ->
        bookRepository.findAllBySeriesId(series.id)
          .filter { (bookMetadataRepository.findById(it.id).number.trimStart('0') == book.number.trimStart('0')) }
      }
      ReadListRequestBookMatches(book, matches)
    }

    return ReadListRequestMatch(readListMatch, matches)
  }
}
