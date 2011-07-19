package com.mhs.qsol.proximity.distribute;

/**
 * Copyright 2006 Mark Miller (markrmiller@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.mhs.qsol.proximity.ProximityBuilder.ProxType;

public class ProxInfo {
  public String distance;
  public boolean ordered;
  ProxType proxType;
  public String paraMarker;
  public String sentMarker;
  public String fieldBreakMarker;

  public ProxInfo(String distance, boolean ordered, ProxType proxType,
      String sentMarker, String paraMarker) {
    this.distance = distance;
    this.ordered = ordered;
    this.proxType = proxType;
    this.paraMarker = paraMarker;

    this.sentMarker = sentMarker;
  }
}
