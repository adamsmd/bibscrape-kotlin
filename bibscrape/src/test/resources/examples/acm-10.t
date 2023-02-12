https://portal.acm.org/citation.cfm?id=512529.512538
Subtitle and capitalization problems

@inproceedings{Das:2002:esp:10.1145/512529.512538,
  author = {Das, Manuvir and Lerner, Sorin and Seigle, Mark},
  title = {{ESP}: path-sensitive program verification in polynomial time},
  booktitle = {Proceedings of the ACM SIGPLAN 2002 Conference on Programming Language Design and Implementation},
  series = {PLDI~'02},
  location = {Berlin, Germany},
  pages = {57--68},
  numpages = {12},
  month = jun,
  year = {2002},
  publisher = {Association for Computing Machinery},
  address = {New York, NY, USA},
  isbn = {1-58113-463-0},
  doi = {10.1145/512529.512538},
  bib_scrape_url = {https://portal.acm.org/citation.cfm?id=512529.512538},
  keywords = {dataflow analysis; error detection; path-sensitive analysis},
  abstract = {In this paper, we present a new algorithm for partial program verification that runs in polynomial time and space. We are interested in checking that a program satisfies a given temporal safety property. Our insight is that by accurately modeling only those branches in a program for which the property-related behavior differs along the arms of the branch, we can design an algorithm that is accurate enough to verify the program with respect to the given property, without paying the potentially exponential cost of full path-sensitive analysis.We have implemented this {\textquotedbl}property simulation{\textquotedbl} algorithm as part of a partial verification tool called ESP. We present the results of applying ESP to the problem of verifying the file I/O behavior of a version of the GNU C compiler (gcc, 140,000 LOC). We are able to prove that all of the 646 calls to \textbf{.fprintf} in the source code of gcc are guaranteed to print to valid, open files. Our results show that property simulation scales to large programs and is accurate enough to verify meaningful properties.},
}
