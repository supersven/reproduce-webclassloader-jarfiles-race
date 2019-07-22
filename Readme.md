# Reproduce a race-condition in WebappClassLoader

Please see [./src/test/java/org/glassfish/web/loader/ReproduceTest.java]

`ReproduceTest.shouldProvokeRaceConditionIn_findResourceInternalFromJars`
concurrently calls `WebappClassLoader.addJar()`, `WebappClassLoader.findResource()` and `WebappClassLoader.closeJARs()`.

The exceptions thrown by `findResource()` (written to the console / `System.out`) show 
that `findResource()` and `closeJARs()` are not properly synchronized with each other.

**Please note:** The occurrence of this problem strongly depends on the threads timings and thus on the executing machine. 
Basically, this works on my machine; you might have to change the timings by playing around with the `Thread.sleep()` 
intervals to provoke the issue.

## Proposed Solution
`closeJARs()` (and some other methods) synchronizes on the field `jarFilesLock`.
`findResource()`'s helper method `findClassInternal()` synchronizes on `jarFiles` and is the only method that does that.

I think it's very likely that it was meant to synchronize on `jarFilesLock`, too.

Additionally IntelliJ warns:

> Synchronization on a non-final field 'jarFiles'
> Reports synchronized statements where the lock expression is a reference to a non-final field. Such statements are unlikely to have useful semantics, as different threads may be locking on different objects even when operating on the same object. 

As e.g. `closeJARs()` mutates the `jarFiles` array, generally all accesses to it should synchronize with `jarFilesLock`.